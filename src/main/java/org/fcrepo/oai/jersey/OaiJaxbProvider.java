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
package org.fcrepo.oai.jersey;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.ndltd.standards.metadata.etdms._1.Thesis;
import org.openarchives.oai._2.OAIPMHtype;
import org.openarchives.oai._2_0.oai_dc.OaiDcType;
import org.openarchives.oai._2_0.oai_identifier.OaiIdentifierType;

import com.google.common.xml.XmlEscapers;
import com.sun.xml.bind.marshaller.CharacterEscapeHandler;
import com.sun.xml.bind.marshaller.NamespacePrefixMapper;

/**
 * The type Oai jaxb provider.
 * 
 * @author Frank Asseg
 * @author Piyapong Charoenwattana
 */
@Provider
public class OaiJaxbProvider implements ContextResolver<Marshaller> {

    @SuppressWarnings("serial")
    private static final Map<String, String> namespacePrefixMap() {
        return Collections.unmodifiableMap(new HashMap<String, String>() {
            {
                put("http://www.w3.org/2001/XMLSchema-instance", "xsi");
                put("http://www.openarchives.org/OAI/2.0/", "");
                put("http://www.openarchives.org/OAI/2.0/oai-identifier", "oai-id");
                put("http://www.openarchives.org/OAI/2.0/oai_dc/", "oai_dc");
                put("http://purl.org/dc/elements/1.1/", "dc");
                put("http://www.ndltd.org/standards/metadata/etdms/1.0/", "etd_ms");
                put("http://www.w3.org/2005/Atom", "atom");
                put("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf");
                put("http://www.w3.org/2000/01/rdf-schema#", "rdfs");
                put("http://www.openarchives.org/ore/atom/", "oreatom");
                put("http://purl.org/dc/terms/", "dcterms");
            }
        });
    }

    private static final String schemaLocation =
        "http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd\n"
            + "    http://www.openarchives.org/OAI/2.0/oai-identifier "
            + "http://www.openarchives.org/OAI/2.0/oai-identifier.xsd\n"
            + "    http://www.openarchives.org/OAI/2.0/oai_dc/ http://www.openarchives.org/OAI/2.0/oai_dc.xsd\n"
            + "    http://www.ndltd.org/standards/metadata/etdms/1.0/ "
            + "    http://www.ndltd.org/standards/metadata/etdms/1.0/etdms.xsd"
            + "    http://www.w3.org/2005/Atom"
            ;

    private final Marshaller marshaller;

    /**
     * Instantiates a new Oai jaxb provider.
     *
     * @throws JAXBException the jAXB exception
     */
    public OaiJaxbProvider() throws JAXBException {
        this.marshaller = JAXBContext
            .newInstance(
                    OaiDcType.class, OaiIdentifierType.class, OAIPMHtype.class, Thesis.class,
                    org.w3._2005.atom.EntryType.class, org.w3._2005.atom.IdType.class,
                    org.openarchives.ore.atom.Triples.class
                    ).createMarshaller();

        this.marshaller.setProperty("com.sun.xml.bind.marshaller.CharacterEscapeHandler", new CharacterEscapeHandler() {

            @Override
            public void escape(final char[] chars, final int start, final int len, final boolean isAttr,
                final Writer writer) throws IOException {
                writer.write(XmlEscapers.xmlContentEscaper().escape(new String(chars)));
            }
        });

        this.marshaller.setProperty("com.sun.xml.bind.namespacePrefixMapper", new NamespacePrefixMapper() {

            @Override
            public String getPreferredPrefix(final String namespaceUri, final String suggestion,
                final boolean requirePrefix) {
                return namespacePrefixMap().get(namespaceUri);
            }
        });
        this.marshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, schemaLocation);
        this.marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
    }

    @Override
    public Marshaller getContext(final Class<?> aClass) {
        if (aClass == OAIPMHtype.class) {
            return this.marshaller;
        }
        return null;
    }
}
