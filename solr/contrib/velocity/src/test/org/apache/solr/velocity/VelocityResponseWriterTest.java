/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.velocity;

import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.SolrParamResourceLoader;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.response.VelocityResponseWriter;
import org.apache.solr.request.SolrQueryRequest;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;

public class VelocityResponseWriterTest extends SolrTestCaseJ4 {
  @BeforeClass
  public static void beforeClass() throws Exception {
    initCore("solrconfig.xml", "schema.xml", getFile("velocity/solr").getAbsolutePath());
    System.out.println(getFile("velocity/solr").getAbsolutePath());
  }

  @Test
  public void testVelocityResponseWriterRegistered() {
    QueryResponseWriter writer = h.getCore().getQueryResponseWriter("velocity");
    assertTrue("VrW registered check", writer instanceof VelocityResponseWriter);
  }

  @Test
  public void testCustomParamTemplate() throws Exception {
    org.apache.solr.response.VelocityResponseWriter vrw = new VelocityResponseWriter();
    NamedList<String> nl = new NamedList<String>();
    nl.add(VelocityResponseWriter.PARAMS_RESOURCE_LOADER_ENABLED, "true");
    vrw.init(nl);
    SolrQueryRequest req = req(VelocityResponseWriter.TEMPLATE,"custom",
        SolrParamResourceLoader.TEMPLATE_PARAM_PREFIX+"custom","$response.response.response_data");
    SolrQueryResponse rsp = new SolrQueryResponse();
    StringWriter buf = new StringWriter();
    rsp.add("response_data", "testing");
    vrw.write(buf, req, rsp);
    assertEquals("testing", buf.toString());
  }

  @Test
  public void testParamResourceLoaderDisabled() throws Exception {
    org.apache.solr.response.VelocityResponseWriter vrw = new VelocityResponseWriter();
    // by default param resource loader is disabled, no need to set it here
    SolrQueryRequest req = req(VelocityResponseWriter.TEMPLATE,"custom",
        SolrParamResourceLoader.TEMPLATE_PARAM_PREFIX+"custom","$response.response.response_data");
    SolrQueryResponse rsp = new SolrQueryResponse();
    StringWriter buf = new StringWriter();
    try {
      vrw.write(buf, req, rsp);
      fail("Should have thrown exception due to missing template");
    } catch (IOException e) {
      // expected exception
    }
  }

  @Test
  public void testFileResourceLoader() throws Exception {
    org.apache.solr.response.VelocityResponseWriter vrw = new VelocityResponseWriter();
    NamedList<String> nl = new NamedList<String>();
    nl.add("template.base.dir", getFile("velocity").getAbsolutePath());
    vrw.init(nl);
    SolrQueryRequest req = req(VelocityResponseWriter.TEMPLATE,"file");
    SolrQueryResponse rsp = new SolrQueryResponse();
    StringWriter buf = new StringWriter();
    vrw.write(buf, req, rsp);
    assertEquals("testing", buf.toString());
  }

  @Test
  public void testSolrResourceLoaderTemplate() throws Exception {
    assertEquals("0", h.query(req("q","*:*", "wt","velocity",VelocityResponseWriter.TEMPLATE,"numFound")));
  }

  @Test
  public void testMacros() throws Exception {
    // tests that a macro in a custom macros.vm is visible
    assertEquals("test_macro_SUCCESS", h.query(req("q","*:*", "wt","velocity",VelocityResponseWriter.TEMPLATE,"test_macro_visible")));

    // tests that a builtin (_macros.vm) macro, #url_root in this case, can be overridden in a custom macros.vm
    // the macro is also defined in VM_global_library.vm, which should also be overridden by macros.vm
    assertEquals("Loaded from: macros.vm", h.query(req("q","*:*", "wt","velocity",VelocityResponseWriter.TEMPLATE,"test_macro_overridden")));

    // tests that macros defined in VM_global_library.vm are visible.  This file was where macros in pre-5.0 versions were defined
    assertEquals("legacy_macro_SUCCESS", h.query(req("q","*:*", "wt","velocity",VelocityResponseWriter.TEMPLATE,"test_macro_legacy_support")));
  }

  @Test
  public void testInitProps() throws Exception {
    // The test init properties file turns off being able to use $foreach.index (the implicit loop counter)
    // The foreach.vm template uses $!foreach.index, with ! suppressing the literal "$foreach.index" output

    assertEquals("01", h.query(req("q","*:*", "wt","velocity",VelocityResponseWriter.TEMPLATE,"foreach")));
    assertEquals("", h.query(req("q","*:*", "wt","velocityWithInitProps",VelocityResponseWriter.TEMPLATE,"foreach")));
  }

  @Test
  public void testLocaleFeature() throws Exception {
    assertEquals("Color", h.query(req("q", "*:*", "wt", "velocity", VelocityResponseWriter.TEMPLATE, "locale",
        VelocityResponseWriter.LOCALE,"en_US")));
    assertEquals("Colour", h.query(req("q", "*:*", "wt", "velocity", VelocityResponseWriter.TEMPLATE, "locale",
        VelocityResponseWriter.LOCALE,"en_UK")));
  }

  @Test
  public void testLayoutFeature() throws Exception {
    assertEquals("{{{0}}}", h.query(req("q","*:*", "wt","velocity",
        VelocityResponseWriter.TEMPLATE,"numFound", VelocityResponseWriter.LAYOUT,"layout")));

    // even with v.layout specified, layout can be disabled explicitly
    assertEquals("0", h.query(req("q","*:*", "wt","velocity",
        VelocityResponseWriter.TEMPLATE,"numFound",
        VelocityResponseWriter.LAYOUT,"layout",
        VelocityResponseWriter.LAYOUT_ENABLED,"false")));
  }

  @Test
  public void testJSONWrapper() throws Exception {
    assertEquals("foo({\"result\":\"0\"})", h.query(req("q", "*:*", "wt", "velocity",
        VelocityResponseWriter.TEMPLATE, "numFound",
        VelocityResponseWriter.JSON,"foo")));

    // Now with layout, for good measure
    assertEquals("foo({\"result\":\"{{{0}}}\"})", h.query(req("q", "*:*", "wt", "velocity",
        VelocityResponseWriter.TEMPLATE, "numFound",
        VelocityResponseWriter.JSON,"foo",
        VelocityResponseWriter.LAYOUT,"layout")));
  }

}
