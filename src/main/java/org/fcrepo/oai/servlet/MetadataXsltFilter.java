/**
 * Copyright 2015 DuraSpace, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fcrepo.oai.servlet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.lang3.StringUtils;

/**
 * The MetadataXsltFilter class.
 *
 * @author Piyapong Charoenwattana
 */
// @WebFilter(filterName = "MetadataXsltFilter", urlPatterns = { "/rest/oai" }, initParams = {
// @WebInitParam(name = "xslPath", value = "/xslt/metadata.xsl") })
public class MetadataXsltFilter implements Filter {

    private String xslPath;
    private TransformerFactory factory;
    private StreamSource xslSource;

    /**
     *
     * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
     */
    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
        this.factory = TransformerFactory.newInstance();
        this.xslPath = filterConfig.getInitParameter("xslPath");
        this.xslSource = new StreamSource(this.getClass().getResourceAsStream(xslPath));
    }

    /**
     *
     * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse,
     *      javax.servlet.FilterChain)
     */
    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
        throws IOException, ServletException {

        final PrintWriter out = response.getWriter();
        final BufferedHttpResponseWrapper wrapper = new BufferedHttpResponseWrapper((HttpServletResponse) response);
        chain.doFilter(request, wrapper);
        final String resp = new String(wrapper.getBuffer());
        if (StringUtils.isEmpty(resp)) {
            chain.doFilter(request, response);
            return;
        }
        final Source xmlSource = new StreamSource(new StringReader(resp));

        try {
            final Transformer transformer = factory.newTransformer(xslSource);
            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            final StreamResult result = new StreamResult(bos);
            transformer.transform(xmlSource, result);
            response.setContentType("text/xml");
            response.setContentLength(bos.size());
            out.write(new String(bos.toByteArray()));
        } catch (final Exception ex) {
            out.println(ex.toString());
            out.write(wrapper.toString());
        }
        out.flush();
    }

    /**
     *
     * @see javax.servlet.Filter#destroy()
     */
    @Override
    public void destroy() {
    }
}