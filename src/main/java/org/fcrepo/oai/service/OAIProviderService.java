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
package org.fcrepo.oai.service;

import static org.apache.jena.rdf.model.ResourceFactory.createProperty;
import static java.util.Collections.emptyMap;
import static org.fcrepo.kernel.modeshape.rdf.converters.PropertyConverter.getPropertyNameFromPredicate;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import static java.util.stream.Collectors.toList;
import java.util.stream.Stream;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.jcr.NamespaceException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.Row;
import javax.jcr.query.RowIterator;
import javax.ws.rs.core.UriInfo;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.namespace.QName;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.fcrepo.http.api.FedoraLdp;
import org.fcrepo.http.api.FedoraNodes;
import org.fcrepo.http.commons.api.rdf.HttpResourceConverter;
import org.fcrepo.http.commons.session.HttpSession;
import org.fcrepo.http.commons.session.SessionFactory;
import org.fcrepo.kernel.api.FedoraSession;
import org.fcrepo.kernel.api.FedoraTypes;
import org.fcrepo.kernel.api.RdfLexicon;
import org.fcrepo.kernel.api.RequiredRdfContext;
import static org.fcrepo.kernel.modeshape.FedoraSessionImpl.getJcrSession;
import static org.fcrepo.kernel.modeshape.utils.FedoraTypesUtils.getJcrNode;
import org.fcrepo.kernel.modeshape.RdfJcrLexicon;
import org.fcrepo.kernel.modeshape.rdf.converters.ValueConverter;
import org.fcrepo.kernel.api.models.Container;
import org.fcrepo.kernel.api.models.FedoraBinary;
import org.fcrepo.kernel.api.models.FedoraResource;
import org.fcrepo.kernel.api.services.BinaryService;
import org.fcrepo.kernel.api.services.ContainerService;
import org.fcrepo.kernel.api.services.NodeService;
import org.fcrepo.oai.generator.JcrOaiDcGenerator;
import org.fcrepo.oai.generator.JcrOaiEtdmsGenerator;
import org.fcrepo.oai.generator.JcrOaiOreGenerator;
import org.fcrepo.oai.http.ResumptionToken;
import org.fcrepo.oai.jersey.XmlDeclarationStrippingInputStream;
import org.fcrepo.oai.rdf.PropertyPredicate;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import org.modeshape.jcr.api.NamespaceRegistry;
import org.modeshape.jcr.api.query.QueryResult;

import org.ndltd.standards.metadata.etdms._1.Thesis;
import org.openarchives.oai._2.DeletedRecordType;
import org.openarchives.oai._2.DescriptionType;
import org.openarchives.oai._2.GetRecordType;
import org.openarchives.oai._2.GranularityType;
import org.openarchives.oai._2.HeaderType;
import org.openarchives.oai._2.IdentifyType;
import org.openarchives.oai._2.ListIdentifiersType;
import org.openarchives.oai._2.ListMetadataFormatsType;
import org.openarchives.oai._2.ListRecordsType;
import org.openarchives.oai._2.ListSetsType;
import org.openarchives.oai._2.MetadataFormatType;
import org.openarchives.oai._2.MetadataType;
import org.openarchives.oai._2.OAIPMHerrorType;
import org.openarchives.oai._2.OAIPMHerrorcodeType;
import org.openarchives.oai._2.OAIPMHtype;
import org.openarchives.oai._2.ObjectFactory;
import org.openarchives.oai._2.RecordType;
import org.openarchives.oai._2.RequestType;
import org.openarchives.oai._2.ResumptionTokenType;
import org.openarchives.oai._2.SetType;
import org.openarchives.oai._2.VerbType;
import org.openarchives.oai._2_0.oai_dc.OaiDcType;
import org.openarchives.oai._2_0.oai_identifier.OaiIdentifierType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.base.Predicate;
import com.google.common.base.Stopwatch;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.graph.Triple;


/**
 * The type OAI provider service.
 *
 * @author lsitu
 * @author Frank Asseg
 * @author Piyapong Charoenwattana
 */
public class OAIProviderService {

    // supported metadata prefixes
    // OAI-ORE - only for Thesis
    private static final String METADATA_PREFIX_ORE = "ore";
    private static final String METADATA_PREFIX_OAI_ETDMS = "oai_etdms";
    private static final String METADATA_PREFIX_OAI_DC = "oai_dc";

    private static final Logger log = LoggerFactory.getLogger(OAIProviderService.class);

    private static final ObjectFactory oaiFactory = new ObjectFactory();

    private static final org.openarchives.oai._2_0.oai_identifier.ObjectFactory idFactory
            = new org.openarchives.oai._2_0.oai_identifier.ObjectFactory();

    private final DatatypeFactory dataFactory;

    private String rootPath;

    private String propertyHasModel;

    private String propertyHasCollectionId;

    private String propertyOaiRepositoryName;

    private String propertyOaiDescription;

    private String propertyOaiAdminEmail;

    private String propertyOaiBaseUrl;

    private Map<String, String> namespaces;

    private boolean setsEnabled;

    private boolean searchEnabled;

    private Map<String, String> descriptiveContent;

    private Map<String, MetadataFormat> metadataFormats;

    private final DateTimeFormatter dateFormat = ISODateTimeFormat.dateTimeNoMillis().withZone(DateTimeZone.UTC);

    private final DateTimeFormatter dateFormatMillis = ISODateTimeFormat.dateTime().withZone(DateTimeZone.UTC);

    private int maxListSize;

    private String baseUrl;

    private String scheme;

    private String repositoryIdentifier;

    private String delimiter;

    private String sampleIdentifier;

    private String idFormat;



    private static final Pattern slashPattern = Pattern.compile("\\/");

    private static final String modelIRCollection
            = "IRCollection\u0018^^\u0018http://www.w3.org/2001/XMLSchema#string";

    private static final String modelIRCommunity
            = "IRCommunity\u0018^^\u0018http://www.w3.org/2001/XMLSchema#string";

    private static final String modelIRItem
            = "IRItem\u0018^^\u0018http://www.w3.org/2001/XMLSchema#string";

    public static final String modelIRThesis
            = "IRThesis\u0018^^\u0018http://www.w3.org/2001/XMLSchema#string";

    private static final String publicAccessRights
            = "http://terms.library.ualberta.ca/public\u0018^^\u0018http://www.w3.org/2001/XMLSchema#string";

    @Autowired
    private BinaryService binaryService;

    @Autowired
    private NodeService nodeService;

    @Autowired
    private SessionFactory sessionFactory;

    @Autowired
    private ContainerService containerService;

    @Autowired
    private JcrOaiDcGenerator jcrOaiDcGenerator;

    @Autowired
    private JcrOaiEtdmsGenerator jcrOaiEtdmsGenerator;

    @Autowired
    private JcrOaiOreGenerator jcrOaiOreGenerator;

    /**
     * Service initialization
     *
     *
     */
    @PostConstruct
    public void init() {
        try {
            /* check if set root node exists */
            final FedoraSession fedoraSession = sessionFactory.getInternalSession();
            final Session session = getJcrSession(fedoraSession);

            final NamespaceRegistry namespaceRegistry
                    = (org.modeshape.jcr.api.NamespaceRegistry) session.getWorkspace().getNamespaceRegistry();

            // Register namespaces
            log.info("Registering namespaces...");
            final Set<Entry<String, String>> entries = namespaces.entrySet();
            for (final Entry<String, String> entry : entries) {

                // unregister uri
                try {
                    final String prefix = namespaceRegistry.getPrefix(entry.getValue());
                    log.debug("unregistering prefix: " + prefix + " <" + entry.getValue() + "> ...");
                    namespaceRegistry.unregisterNamespace(prefix);
                } catch (final NamespaceException e) {
                    log.warn("no prefix for <" + entry.getValue() + ">!");
                }

                // register prefix
                if (!(namespaceRegistry.isRegisteredPrefix(entry.getKey())
                        && namespaceRegistry.isRegisteredUri(entry.getValue()))) {
                    log.debug("registering prefix: " + entry.getKey() + " <" + entry.getValue() + "> ...");
                    namespaceRegistry.registerNamespace(entry.getKey(), entry.getValue());
                }
            }

            final Container root;
            if (!nodeService.exists(fedoraSession, rootPath)) {
                log.info("Initializing OAI root {} ...", rootPath);
                root = containerService.findOrCreate(fedoraSession, rootPath);
            } else {
                log.info("Updating OAI root {} ...", rootPath);
                root = containerService.findOrCreate(fedoraSession, rootPath);
            }

            final String repositoryName = descriptiveContent.get("repositoryName");
            final String description = descriptiveContent.get("description");
            final String adminEmail = descriptiveContent.get("adminEmail");
            baseUrl = descriptiveContent.get("baseUrl");
            scheme = descriptiveContent.get("scheme");
            repositoryIdentifier = descriptiveContent.get("repositoryIdentifier");
            delimiter = descriptiveContent.get("delimiter");
            sampleIdentifier = descriptiveContent.get("sampleIdentifier");

            // save data in oai node
            final Node rootNode = getJcrNode(root);
            rootNode.setProperty(getPropertyName(session, createProperty(propertyOaiRepositoryName)),
                    repositoryName);
            rootNode.setProperty(getPropertyName(session, createProperty(propertyOaiDescription)), description);
            rootNode.setProperty(getPropertyName(session, createProperty(propertyOaiAdminEmail)), adminEmail);
            rootNode.setProperty(getPropertyName(session, createProperty(propertyOaiBaseUrl)), baseUrl);
            session.save();
            log.info("OAI Provider started.");

        } catch (final Throwable t) {
            log.error("Could not initialize oai provider!", t);
        }

    }

    /**
     * The destroy method.
     */
    @PreDestroy
    public void destroy() {
        log.info("Shutting down OAI Provider...");
    }

    /**
     * Instantiates a new OAI provider service.
     *
     * @throws DatatypeConfigurationException the datatype configuration
     * exception
     * @throws JAXBException the jAXB exception
     */
    public OAIProviderService() throws DatatypeConfigurationException, JAXBException {
        dataFactory = DatatypeFactory.newInstance();
        final JAXBContext ctx = JAXBContext.newInstance(
                OAIPMHtype.class, IdentifyType.class, SetType.class,
                org.w3._2005.atom.EntryType.class, org.w3._2005.atom.IdType.class,
                org.openarchives.ore.atom.Triples.class
        );
        ctx.createUnmarshaller();
    }

    /**
     * Identify jAXB element.
     *
     * @param session the session
     * @param uriInfo the uri info
     * @return the jAXB element
     * @throws RepositoryException the repository exception
     * @throws JAXBException the jAXB exception
     */
    public JAXBElement<OAIPMHtype> identify(final Session session, final UriInfo uriInfo)
            throws RepositoryException, JAXBException {

        final FedoraSession fedoraSession = sessionFactory.getInternalSession();
        final HttpSession httpSession = new HttpSession(fedoraSession);

        final HttpResourceConverter converter
                = new HttpResourceConverter(httpSession, uriInfo.getBaseUriBuilder().clone().path(FedoraNodes.class));

        final FedoraResource root = nodeService.find(fedoraSession, rootPath);

        final IdentifyType id = oaiFactory.createIdentifyType();
        id.setEarliestDatestamp(dateFormat.print(root.getCreatedDate().toEpochMilli()));
        id.setProtocolVersion("2.0");

        java.util.function.Predicate<Triple> predicate;
        Stream<Triple> triples;

        // repository name, project version
        predicate = new PropertyPredicate(propertyOaiRepositoryName);
        triples = root.getTriples(converter, RequiredRdfContext.PROPERTIES).filter(predicate);
        id.setRepositoryName(triples.iterator().next().getObject().getLiteralValue().toString());

        // base url
        predicate = new PropertyPredicate(propertyOaiBaseUrl);
        triples = root.getTriples(converter, RequiredRdfContext.PROPERTIES).filter(predicate);
        final String baseUrl = triples.iterator().next().getObject().getLiteralValue().toString();
        id.setBaseURL(baseUrl);

        // admin email
        predicate = new PropertyPredicate(propertyOaiAdminEmail);
        triples = root.getTriples(converter, RequiredRdfContext.PROPERTIES).filter(predicate);
        id.getAdminEmail().add(0, triples.iterator().next().getObject().getLiteralValue().toString());

        // granularity
        id.setGranularity(GranularityType.YYYY_MM_DD_THH_MM_SS_Z);

        // deleteRecord
        id.setDeletedRecord(DeletedRecordType.NO);

        // oai-identifier description
        final OaiIdentifierType oaiIdType = idFactory.createOaiIdentifierType();
        oaiIdType.setScheme(scheme);
        oaiIdType.setRepositoryIdentifier(repositoryIdentifier);
        oaiIdType.setDelimiter(delimiter);
        oaiIdType.setSampleIdentifier(sampleIdentifier);
        final DescriptionType desc = oaiFactory.createDescriptionType();
        desc.setAny(idFactory.createOaiIdentifier(oaiIdType));
        id.getDescription().add(desc);

        final OAIPMHtype oai = oaiFactory.createOAIPMHtype();

        // request
        oai.setRequest(createRequest(VerbType.IDENTIFY, uriInfo));

        // response
        oai.setResponseDate(dataFactory.newXMLGregorianCalendar(dateFormat.print(new Date().getTime())));
        oai.setIdentify(id);
        return oaiFactory.createOAIPMH(oai);
    }

    /**
     * List metadata formats.
     *
     * @param session the session
     * @param uriInfo the uri info
     * @param identifier the identifier
     * @return the jAXB element
     * @throws RepositoryException the repository exception
     */
    public JAXBElement<OAIPMHtype> listMetadataFormats(final Session session, final UriInfo uriInfo,
            final String identifier) throws RepositoryException {

        final FedoraSession fedoraSession = sessionFactory.getInternalSession();
        final HttpSession httpSession = new HttpSession(fedoraSession);

        final ListMetadataFormatsType listMetadataFormats = oaiFactory.createListMetadataFormatsType();
        final HttpResourceConverter converter
                = new HttpResourceConverter(httpSession, uriInfo.getBaseUriBuilder().clone().path(FedoraNodes.class));

        /* check which formats are available on top of oai_dc for this object */
        if (identifier != null && !identifier.isEmpty()) {

            final String noid;
            try {
                noid = getNoidFromIdentifier(identifier);
            } catch (final Exception e) {
                return error(VerbType.GET_RECORD, identifier, null, OAIPMHerrorcodeType.ID_DOES_NOT_EXIST,
                        "The requested identifier does not exist");
            }

            final String path = getPathFromNoid(session, noid, null);
            if (path == null) {
                return error(VerbType.GET_RECORD, identifier, null, OAIPMHerrorcodeType.ID_DOES_NOT_EXIST,
                        "The requested identifier does not exist");
            }

            // final String path = identifier.substring(this.baseUrl.length());
            if (!path.isEmpty()) {
                /* generate metadata format response for a single pid */
                if (!nodeService.exists(fedoraSession, path)) {
                    return error(VerbType.LIST_METADATA_FORMATS, identifier, null,
                            OAIPMHerrorcodeType.ID_DOES_NOT_EXIST, "The object does not exist");
                }
                final Container obj = containerService.find(fedoraSession, path);
                for (final MetadataFormat mdf : metadataFormats.values()) {
                    if (mdf.getPrefix().equals("oai_dc")) {
                        listMetadataFormats.getMetadataFormat().add(mdf.asMetadataFormatType());
                    } else if (mdf.getPrefix().equals("oai_etdms")) {
                        listMetadataFormats.getMetadataFormat().add(mdf.asMetadataFormatType());
                    } else if (mdf.getPrefix().equals(METADATA_PREFIX_ORE)) {
                        listMetadataFormats.getMetadataFormat().add(mdf.asMetadataFormatType());
                    } else {
                        // FIXME: should check on dcterms:type == 'Thesis' ? oai_dc and oai_etdms : oai_dc
                        java.util.function.Predicate<Triple> predicate;
                        Stream<Triple> triples;
                        predicate = new PropertyPredicate(mdf.getPropertyName());
                        triples = obj.getTriples(converter, RequiredRdfContext.PROPERTIES).filter(predicate);
                        if (triples.iterator().hasNext()) {
                            listMetadataFormats.getMetadataFormat().add(mdf.asMetadataFormatType());
                        }
                    }
                }
            }
        } else {
            /* generate a general metadata format response */
            listMetadataFormats.getMetadataFormat().addAll(listAvailableMetadataFormats());
        }
        final OAIPMHtype oai = oaiFactory.createOAIPMHtype();

        // request
        oai.setRequest(createRequest(VerbType.LIST_METADATA_FORMATS, uriInfo));

        // response
        oai.setResponseDate(dataFactory.newXMLGregorianCalendar(dateFormat.print(new Date().getTime())));
        oai.setListMetadataFormats(listMetadataFormats);
        return oaiFactory.createOAIPMH(oai);
    }

    private List<MetadataFormatType> listAvailableMetadataFormats() {
        final List<MetadataFormatType> types = new ArrayList<>(metadataFormats.size());
        for (final MetadataFormat mdf : metadataFormats.values()) {
            final MetadataFormatType mdft = oaiFactory.createMetadataFormatType();
            mdft.setMetadataPrefix(mdf.getPrefix());
            mdft.setMetadataNamespace(mdf.getNamespace());
            mdft.setSchema(mdf.getSchemaUrl());
            types.add(mdft);
        }
        return types;
    }

    /**
     * Gets record.
     *
     * @param session the session
     * @param uriInfo the uri info
     * @param identifier the identifier
     * @param metadataPrefix the metadata prefix
     * @return the record
     * @throws RepositoryException the repository exception
     */
    public JAXBElement<OAIPMHtype> getRecord(final Session session, final UriInfo uriInfo, final String identifier,
            final String metadataPrefix) throws RepositoryException {

        final FedoraSession fedoraSession = sessionFactory.getInternalSession();

        final MetadataFormat format = metadataFormats.get(metadataPrefix);

        if (identifier == null || metadataPrefix == null) {
            return error(VerbType.GET_RECORD, identifier, metadataPrefix, OAIPMHerrorcodeType.BAD_ARGUMENT,
                    "The request includes illegal arguments or is missing required arguments.");
        }
        if (format == null) {
            return error(VerbType.GET_RECORD, identifier, metadataPrefix, OAIPMHerrorcodeType.CANNOT_DISSEMINATE_FORMAT,
                    "The metadata format is not available");
        }

        final String noid;
        try {
            noid = getNoidFromIdentifier(identifier);
        } catch (final Exception e) {
            return error(VerbType.GET_RECORD, identifier, metadataPrefix, OAIPMHerrorcodeType.ID_DOES_NOT_EXIST,
                    "The requested identifier does not exist");
        }

        final String path = getPathFromNoid(session, noid, metadataPrefix);
        if (path == null) {
            return error(VerbType.GET_RECORD, identifier, metadataPrefix, OAIPMHerrorcodeType.ID_DOES_NOT_EXIST,
                    "The requested identifier does not exist");
        }

        final Container obj = containerService.find(fedoraSession, path);

        final OAIPMHtype oai = oaiFactory.createOAIPMHtype();

        // request
        oai.setRequest(createRequest(VerbType.GET_RECORD, uriInfo));

        // response
        oai.setResponseDate(dataFactory.newXMLGregorianCalendar(dateFormat.print(new Date().getTime())));
        final GetRecordType getRecord = oaiFactory.createGetRecordType();
        final RecordType record;
        try {
            record = createRecord(session, format, obj.getPath(), noid, uriInfo);
            getRecord.setRecord(record);
            oai.setGetRecord(getRecord);
            return oaiFactory.createOAIPMH(oai);
        } catch (final IOException e) {
            log.error("Unable to create OAI record for object " + obj.getPath());
            return error(VerbType.GET_RECORD, identifier, metadataPrefix, OAIPMHerrorcodeType.ID_DOES_NOT_EXIST,
                    "The requested OAI record does not exist for object " + obj.getPath());
        }
    }

    private JAXBElement<OaiDcType> generateOaiDc(
            final Container obj,
            final String name,
            final ValueConverter valueConverter
            ) throws RepositoryException {
        return jcrOaiDcGenerator.generate(obj, name, valueConverter);
    }

    private Thesis generateOaiEtdms(
            final Container obj,
            final String name,
            final ValueConverter valueConverter,
            final HttpResourceConverter resourceConverter,
            final FedoraSession fedoraSession
            ) throws RepositoryException {
        // retrieve the associated file container
        final List<FedoraBinary> fileContainerList = getFileResources(obj, resourceConverter, fedoraSession);
        return jcrOaiEtdmsGenerator.generate(obj, name, valueConverter, resourceConverter, fileContainerList);
    }

    private JAXBElement<org.w3._2005.atom.EntryType> generateOaiOre(
            final Container obj,
            final String name,
            final ValueConverter valueConverter,
            final HttpResourceConverter resourceConverter,
            final FedoraSession fedoraSession,
            final String identifier
            ) throws RepositoryException {
        final List<FedoraBinary> fileContainerList = getFileResources(obj, resourceConverter, fedoraSession);
        return jcrOaiOreGenerator.generate(obj, name, valueConverter, resourceConverter, fileContainerList, identifier);
    }

    private JAXBElement<String> fetchOaiResponse(
            final Container obj, final FedoraSession fedoraSession, final HttpSession httpSession,
            final MetadataFormat format, final UriInfo uriInfo) throws IOException {

        final HttpResourceConverter converter
                = new HttpResourceConverter(httpSession, uriInfo.getBaseUriBuilder().clone().path(FedoraNodes.class));

        final java.util.function.Predicate<Triple> predicate
                = new PropertyPredicate(format.getPropertyName());
        final Stream<Triple> triples
                = obj.getTriples(converter, RequiredRdfContext.PROPERTIES).filter(predicate);

        if (!triples.iterator().hasNext()) {
            log.error("There is no OAI record of type " + format.getPrefix() + " associated with the object "
                    + obj.getPath());
            return null;
        }

        final String recordPath = triples.iterator().next().getObject().getLiteralValue().toString();
        final FedoraBinary bin = binaryService.findOrCreate(fedoraSession, "/" + recordPath);

        try (final InputStream src = new XmlDeclarationStrippingInputStream(bin.getContent())) {
            return new JAXBElement<>(new QName(format.getPrefix()), String.class, IOUtils.toString(src));
        }
    }

    /**
     * Creates a OAI error response for JAX-B
     *
     * @param verb the verb
     * @param identifier the identifier
     * @param metadataPrefix the metadata prefix
     * @param errorCode the error code
     * @param msg the msg
     * @return the jAXB element
     */
    public JAXBElement<OAIPMHtype> error(final VerbType verb, final String identifier, final String metadataPrefix,
            final OAIPMHerrorcodeType errorCode, final String msg) {
        final OAIPMHtype oai = oaiFactory.createOAIPMHtype();
        final RequestType req = oaiFactory.createRequestType();
        req.setVerb(verb);
        req.setIdentifier(StringEscapeUtils.escapeXml(identifier));
        req.setMetadataPrefix(metadataPrefix);
        oai.setRequest(req);
        oai.setResponseDate(dataFactory.newXMLGregorianCalendar(dateFormat.print(new Date().getTime())));

        final OAIPMHerrorType error = oaiFactory.createOAIPMHerrorType();
        error.setCode(errorCode);
        error.setValue(msg);
        oai.getError().add(error);
        return oaiFactory.createOAIPMH(oai);
    }

    /**
     * List identifiers.
     *
     * @param session the session
     * @param uriInfo the uri info
     * @param metadataPrefix the metadata prefix
     * @param from the from
     * @param until the until
     * @param set the set
     * @param offset the offset
     * @return the jAXB element
     * @throws RepositoryException the repository exception
     */
    public JAXBElement<OAIPMHtype> listIdentifiers(final Session session, final UriInfo uriInfo,
            final String metadataPrefix, final String from, final String until, final String set, final int offset)
            throws RepositoryException {

        final FedoraSession fedoraSession = sessionFactory.getInternalSession();
        final HttpSession httpSession = new HttpSession(fedoraSession);

        if (metadataPrefix == null) {
            return error(VerbType.LIST_IDENTIFIERS, null, null, OAIPMHerrorcodeType.BAD_ARGUMENT,
                    "metadataprefix is invalid");
        }

        final MetadataFormat mdf = metadataFormats.get(metadataPrefix);
        if (mdf == null) {
            return error(VerbType.LIST_IDENTIFIERS, null, metadataPrefix, OAIPMHerrorcodeType.CANNOT_DISSEMINATE_FORMAT,
                    "Unavailable metadata format");
        }

        if (StringUtils.isNotBlank(set) && !setsEnabled) {
            return error(VerbType.LIST_IDENTIFIERS, null, metadataPrefix, OAIPMHerrorcodeType.NO_SET_HIERARCHY,
                    "Sets are not enabled");
        }

        // dateTime format validation
        try {
            validateDateTimeFormat(from);
            validateDateTimeFormat(until);
        } catch (final IllegalArgumentException e) {
            return error(VerbType.LIST_IDENTIFIERS, null, metadataPrefix, OAIPMHerrorcodeType.BAD_ARGUMENT,
                    e.getMessage());
        }

        final HttpResourceConverter converter
                = new HttpResourceConverter(httpSession, uriInfo.getBaseUriBuilder().clone().path(FedoraNodes.class));
        final ValueConverter valueConverter = new ValueConverter(session, converter);

        final String jql = listResourceQuery(session, FedoraTypes.FEDORA_CONTAINER, metadataPrefix, from, until, set,
                maxListSize, offset);

        try {
            final QueryManager queryManager = session.getWorkspace().getQueryManager();
            final RowIterator result = executeQuery(queryManager, jql);

            if (!result.hasNext()) {
                return error(VerbType.LIST_IDENTIFIERS, null, metadataPrefix, OAIPMHerrorcodeType.NO_RECORDS_MATCH,
                        "No record found");
            }

            final OAIPMHtype oai = oaiFactory.createOAIPMHtype();
            oai.setResponseDate(dataFactory.newXMLGregorianCalendar(dateFormat.print(new Date().getTime())));
            final ListIdentifiersType ids = oaiFactory.createListIdentifiersType();
            java.util.function.Predicate<Triple> predicate;
            Stream<Triple> triples;

            while (result.hasNext()) {
                final HeaderType h = oaiFactory.createHeaderType();
                final Row sol = result.nextRow();
                final Resource sub = valueConverter.convert(sol.getValue("sub")).asResource();
                final String path = converter.convert(sub).getPath();

                // get base url
                final FedoraResource root = nodeService.find(fedoraSession, rootPath);
                predicate = new PropertyPredicate(propertyOaiBaseUrl);
                triples = root.getTriples(converter, RequiredRdfContext.PROPERTIES).filter(predicate);
                h.setIdentifier(createId(converter.asString(sub)));

                final Container obj = containerService.find(fedoraSession, path);
                h.setDatestamp(dateFormat.print(obj.getLastModifiedDate().toEpochMilli()));
                predicate = new PropertyPredicate(propertyHasCollectionId);
                triples = obj.getTriples(converter, RequiredRdfContext.PROPERTIES).filter(predicate);
                final List<Triple> tl = triples.collect(toList());
                tl.forEach((tmp) -> { h.getSetSpec().add(tmp.getObject().getLiteralValue().toString()); });

                ids.getHeader().add(h);
            }

            // resumptionToken
            if (ids.getHeader().size() == maxListSize) {
                final ResumptionTokenType token = oaiFactory.createResumptionTokenType();
                token.setValue(encodeResumptionToken(VerbType.LIST_IDENTIFIERS.value(), metadataPrefix, from, until,
                        set, offset + maxListSize));
                token.setCursor(new BigInteger(String.valueOf(offset)));
                token.setCompleteListSize(new BigInteger(String.valueOf(result.getSize())));
                ids.setResumptionToken(token);
            }

            // request
            oai.setRequest(createRequest(VerbType.LIST_IDENTIFIERS, uriInfo));

            // response
            oai.setListIdentifiers(ids);
            return oaiFactory.createOAIPMH(oai);
        } catch (final Exception e) {
            e.printStackTrace();
            throw new RepositoryException(e);
        }
    }

    /**
     * Encode resumption token.
     *
     * @param verb the verb
     * @param metadataPrefix the metadata prefix
     * @param from the from
     * @param until the until
     * @param set the set
     * @param offset the offset
     * @return the string
     * @throws UnsupportedEncodingException the unsupported encoding exception
     */
    public static String encodeResumptionToken(final String verb, final String metadataPrefix, final String from,
            final String until, final String set, final int offset) throws UnsupportedEncodingException {

        final String[] data = new String[]{urlEncode(verb), urlEncode(metadataPrefix != null ? metadataPrefix : ""),
            urlEncode(from != null ? from : ""), urlEncode(until != null ? until : ""),
            urlEncode(set != null ? set : ""), urlEncode(String.valueOf(offset))};
        return Base64.encodeBase64URLSafeString(StringUtils.join(data, ':').getBytes("UTF-8"));
    }

    /**
     * Url encode.
     *
     * @param value the value
     * @return the string
     * @throws UnsupportedEncodingException the unsupported encoding exception
     */
    public static String urlEncode(final String value) throws UnsupportedEncodingException {
        return URLEncoder.encode(value, "UTF-8");
    }

    /**
     * Url decode.
     *
     * @param value the value
     * @return the string
     * @throws UnsupportedEncodingException the unsupported encoding exception
     */
    public static String urlDecode(final String value) throws UnsupportedEncodingException {
        return URLDecoder.decode(value, "UTF-8");
    }

    /**
     * Decode resumption token.
     *
     * @param token the token
     * @return the resumption token
     * @throws UnsupportedEncodingException the unsupported encoding exception
     */
    public static ResumptionToken decodeResumptionToken(final String token) throws UnsupportedEncodingException {
        final String[] data = StringUtils.splitPreserveAllTokens(new String(Base64.decodeBase64(token)), ':');
        final String verb = urlDecode(data[0]);
        final String metadataPrefix = urlDecode(data[1]);
        final String from = urlDecode(data[2]);
        final String until = urlDecode(data[3]);
        final String set = urlDecode(data[4]);
        final int offset = Integer.parseInt(urlDecode(data[5]));
        return new ResumptionToken(verb, metadataPrefix, from, until, offset, set);
    }

    /**
     * List sets.
     *
     * @param session the session
     * @param uriInfo the uri info
     * @param offset the offset
     * @return the jAXB element
     * @throws RepositoryException the repository exception
     */
    public JAXBElement<OAIPMHtype> listSets(final Session session, final UriInfo uriInfo, final int offset)
            throws RepositoryException {

        final FedoraSession fedoraSession = sessionFactory.getInternalSession();
        final HttpSession httpSession = new HttpSession(fedoraSession);

        final HttpResourceConverter converter
                = new HttpResourceConverter(httpSession, uriInfo.getBaseUriBuilder().clone().path(FedoraLdp.class));
        final ValueConverter valueConverter = new ValueConverter(session, converter);

        try {
            if (!setsEnabled) {
                return error(VerbType.LIST_SETS, null, null, OAIPMHerrorcodeType.NO_SET_HIERARCHY,
                        "Set are not enabled");
            }

            final QueryManager queryManager = session.getWorkspace().getQueryManager();
            final OAIPMHtype oai = oaiFactory.createOAIPMHtype();
            oai.setResponseDate(dataFactory.newXMLGregorianCalendar(dateFormat.print(new Date().getTime())));

            // request
            oai.setRequest(createRequest(VerbType.LIST_SETS, uriInfo));

            // response
            final ListSetsType sets = oaiFactory.createListSetsType();

            // store community names in cache
            final StringBuilder cjql = new StringBuilder();
            cjql.append("SELECT com.[mode:localName] AS id, com.[dcterms:title] as name ");
            cjql.append("FROM [").append(FedoraTypes.FEDORA_RESOURCE).append("] as com ");
            cjql.append("WHERE com.[model:hasModel] = '").append(modelIRCommunity).append("' ");

            log.debug(cjql.toString());

            final RowIterator res = executeQuery(queryManager, cjql.toString());
            final HashMap<String, String> com = new HashMap<>();
            while (res.hasNext()) {
                final Row sol = res.nextRow();
                log.debug(
                    valueConverter.convert(sol.getValue("id")).asLiteral().getString()
                    );
                com.put(valueConverter.convert(sol.getValue("id")).asLiteral().getString(),
                        valueConverter.convert(sol.getValue("name")).asLiteral().getString());
            }

            // query official collections
            // assumes collection is a memberOf a community
            final StringBuilder jql = new StringBuilder();
            jql.append("SELECT col.[mode:localName] AS spec, col.[dcterms:title] AS name, ")
                    .append("col.[ual:path] as cid ");
            jql.append(" FROM [").append(FedoraTypes.FEDORA_RESOURCE).append("] as col ");
            jql.append(" WHERE col.[model:hasModel] = '").append(modelIRCollection).append("'");

            if (maxListSize > 0) {
                jql.append(" LIMIT ").append(maxListSize);
                jql.append(" OFFSET ").append(offset);
            }

            final RowIterator result = executeQuery(queryManager, jql.toString());
            if (!result.hasNext()) {
                return error(
                        VerbType.LIST_SETS, null, null, OAIPMHerrorcodeType.NO_RECORDS_MATCH,
                        "Zero collection records found"
                );
            }
            while (result.hasNext()) {
                final SetType set = oaiFactory.createSetType();
                final Row sol = result.nextRow();

                // lookup the community via the map for the current collection
                final Value cid = sol.getValue("cid");
                String comStr = null;
                String comIdStr = null;
                if (cid != null) {
                    comIdStr = valueConverter.convert(cid).asLiteral().getString();
                    comStr = com.get(comIdStr);
                }

                // create setName: community name / collection name
                // if there is no community name then
                // remove "community name" and "/"
                final Value name = sol.getValue("name");
                String setName = null;
                if (comStr != null && name != null) {
                    setName = comStr + " / " + valueConverter.convert(name).asLiteral().getString();
                } else if (comStr == null && name != null) {
                    setName = valueConverter.convert(name).asLiteral().getString();
                } else {
                    setName = "";
                }
                // setSpec
                final Value id = sol.getValue("spec");
                final String idStr = valueConverter.convert(id).asLiteral().getString();
                String setSpec = null;
                if (comIdStr != null && id != null) {
                    setSpec = comIdStr + "/" + idStr;
                } else if (comIdStr == null && idStr != null) {
                    setSpec = idStr;
                } else {
                    setSpec = "";
                }

                // spec
                set.setSetSpec(setSpec);
                set.setSetName(setName);
                sets.getSet().add(set);
            }

            // resumptionToken
            if (sets.getSet().size() == maxListSize) {
                final ResumptionTokenType token = oaiFactory.createResumptionTokenType();
                token.setValue(
                        encodeResumptionToken(VerbType.LIST_SETS.value(), null, null, null, null, offset + maxListSize)
                        );
                token.setCursor(new BigInteger(String.valueOf(offset)));
                token.setCompleteListSize(new BigInteger(String.valueOf(result.getSize() + offset)));
                sets.setResumptionToken(token);
            }

            // response
            oai.setListSets(sets);
            return oaiFactory.createOAIPMH(oai);
        } catch (final Exception e) {
            e.printStackTrace();
            throw new RepositoryException(e);
        }
    }

    private RequestType createRequest(final VerbType verb, final UriInfo uriInfo) {
        final RequestType req = oaiFactory.createRequestType();
        req.setVerb(verb);
        req.setFrom(uriInfo.getQueryParameters().getFirst("from"));
        req.setIdentifier(uriInfo.getQueryParameters().getFirst("identifier"));
        req.setMetadataPrefix(uriInfo.getQueryParameters().getFirst("metadataPrefix"));
        req.setResumptionToken(uriInfo.getQueryParameters().getFirst("resumptionToken"));
        req.setSet(uriInfo.getQueryParameters().getFirst("set"));
        req.setUntil(uriInfo.getQueryParameters().getFirst("until"));
        req.setValue(baseUrl);

        return req;
    }

    /**
     * List records.
     *
     * @param session the session
     * @param uriInfo the uri info
     * @param metadataPrefix the metadata prefix
     * @param from the from
     * @param until the until
     * @param set the set
     * @param offset the offset
     * @return the jAXB element
     * @throws RepositoryException the repository exception
     */
    public JAXBElement<OAIPMHtype> listRecords(final Session session, final UriInfo uriInfo,
            final String metadataPrefix, final String from, final String until, final String set, final int offset)
            throws RepositoryException {

        final FedoraSession fedoraSession = sessionFactory.getInternalSession();
        final HttpSession httpSession = new HttpSession(fedoraSession);

        final HttpResourceConverter converter
                = new HttpResourceConverter(httpSession, uriInfo.getBaseUriBuilder().clone().path(FedoraNodes.class));
        final ValueConverter valueConverter = new ValueConverter(session, converter);

        if (metadataPrefix == null) {
            return error(VerbType.LIST_RECORDS, null, null, OAIPMHerrorcodeType.BAD_ARGUMENT,
                    "metadataPrefix is invalid");
        }
        final MetadataFormat mdf = metadataFormats.get(metadataPrefix);
        if (mdf == null) {
            return error(VerbType.LIST_RECORDS, null, metadataPrefix, OAIPMHerrorcodeType.CANNOT_DISSEMINATE_FORMAT,
                    "Unavailable metadata format");
        }

        // dateTime format validation
        try {
            validateDateTimeFormat(from);
            validateDateTimeFormat(until);
        } catch (final IllegalArgumentException e) {
            return error(VerbType.LIST_RECORDS, null, metadataPrefix, OAIPMHerrorcodeType.BAD_ARGUMENT, e.getMessage());
        }

        if (StringUtils.isNotBlank(set) && !setsEnabled) {
            return error(VerbType.LIST_RECORDS, null, metadataPrefix, OAIPMHerrorcodeType.NO_SET_HIERARCHY,
                    "Sets are not enabled");
        }

        final String jql = listResourceQuery(session, FedoraTypes.FEDORA_CONTAINER, metadataPrefix, from, until, set,
                maxListSize, offset);
        try {

            final QueryManager queryManager = session.getWorkspace().getQueryManager();
            final RowIterator result = executeQuery(queryManager, jql);

            if (!result.hasNext()) {
                return error(VerbType.LIST_RECORDS, null, metadataPrefix, OAIPMHerrorcodeType.NO_RECORDS_MATCH,
                        "No record found");
            }

            final OAIPMHtype oai = oaiFactory.createOAIPMHtype();
            oai.setResponseDate(dataFactory.newXMLGregorianCalendar(dateFormat.print(new Date().getTime())));

            final ListRecordsType records = oaiFactory.createListRecordsType();
            while (result.hasNext()) {
                final Row solution = result.nextRow();
                final Resource subjectUri = valueConverter.convert(solution.getValue("sub")).asResource();
                final String name = valueConverter.convert(solution.getValue("name")).asLiteral().getString();
                final RecordType record = createRecord(session, mdf, converter.asString(subjectUri), name, uriInfo);
                records.getRecord().add(record);
            }

            if (records.getRecord().size() == maxListSize) {
                final ResumptionTokenType token = oaiFactory.createResumptionTokenType();
                token.setValue(encodeResumptionToken(VerbType.LIST_RECORDS.value(), metadataPrefix, from, until, set,
                        offset + maxListSize));
                token.setCursor(new BigInteger(String.valueOf(offset)));
                token.setCompleteListSize(new BigInteger(String.valueOf(result.getSize() + offset)));
                records.setResumptionToken(token);
            }

            // request
            oai.setRequest(createRequest(VerbType.LIST_RECORDS, uriInfo));

            // response
            oai.setListRecords(records);
            return oaiFactory.createOAIPMH(oai);
        } catch (final Exception e) {
            e.printStackTrace();
            throw new RepositoryException(e);
        }
    }

    private RecordType createRecord(
            final Session session, final MetadataFormat mdf, final String path,
            final String name, final UriInfo uriInfo)
            throws IOException, RepositoryException {

        final FedoraSession fedoraSession = sessionFactory.getInternalSession();
        final HttpSession httpSession = new HttpSession(fedoraSession);

        final HttpResourceConverter converter
                = new HttpResourceConverter(httpSession, uriInfo.getBaseUriBuilder().clone().path(FedoraNodes.class));
        final ValueConverter valueConverter = new ValueConverter(session, converter);
        final HeaderType h = oaiFactory.createHeaderType();

        // using spring bean property, descriptiveContent.baseUrl
        h.setIdentifier(createId(path));

        final Container obj = containerService.find(fedoraSession, path);
        h.setDatestamp(dateFormat.print(obj.getLastModifiedDate().toEpochMilli()));

        // set setSpecs
        final java.util.function.Predicate<Triple> predicate
                = new PropertyPredicate(propertyHasCollectionId);
        final Stream<Triple> triples
                = obj.getTriples(converter, RequiredRdfContext.PROPERTIES).filter(predicate);
        final List<Triple> tl = triples.collect(toList());
        tl.forEach((tmp) -> { h.getSetSpec().add(tmp.getObject().getLiteralValue().toString()); });

        // get the metadata record from fcrepo
        final MetadataType md = oaiFactory.createMetadataType();
        if (mdf.getPrefix().equals(METADATA_PREFIX_OAI_DC)) {
            /* generate a OAI DC reponse using the DC Generator from fcrepo4 */
            md.setAny(generateOaiDc(obj, name, valueConverter));
        } else if (mdf.getPrefix().equals(METADATA_PREFIX_OAI_ETDMS)) {
            /* generate a OAI ETDMS reponse using the DC Generator from fcrepo4 */
            md.setAny(generateOaiEtdms(obj, name, valueConverter, converter, fedoraSession));
        } else if (mdf.getPrefix().equals(METADATA_PREFIX_ORE)) {
            /* generate a OAI ORE reponse using the DC Generator from fcrepo4 */
            md.setAny(generateOaiOre(obj, name, valueConverter, converter, fedoraSession, h.getIdentifier()));
        } else {
            /* generate a OAI response from the linked Binary */
            md.setAny(fetchOaiResponse(obj, fedoraSession, httpSession, mdf, uriInfo));
        }

        final RecordType record = oaiFactory.createRecordType();
        record.setMetadata(md);
        record.setHeader(h);
        return record;
    }

    /**
     * The createId method.
     *
     * @param path
     * @return
     */
    private String createId(final String path) {
        final String noid = slashPattern.split(path)[6];
        final String id = String.format(idFormat, noid);
        return id;
    }

    private String listResourceQuery(final Session session, final String mixinTypes, final String metadataPrefix,
            final String from, final String until, final String set, final int limit, final int offset)
            throws RepositoryException {

        final String propJcrPath = getPropertyName(session, createProperty(RdfJcrLexicon.JCR_NAMESPACE + "path"));
        final String propHasMixinType = getPropertyName(session, RdfJcrLexicon.HAS_MIXIN_TYPE);
        final String propJcrUuid = getPropertyName(session, createProperty(RdfJcrLexicon.JCR_NAMESPACE + "uuid"));
        final String propJcrLastModifiedDate = getPropertyName(session, RdfLexicon.LAST_MODIFIED_DATE);
        final String propHasModel = getPropertyName(session, createProperty(propertyHasModel));
        final String propHasCollectionId = getPropertyName(session, createProperty(propertyHasCollectionId));

        final StringBuilder jql = new StringBuilder();
        jql.append("SELECT res.[" + propJcrPath + "] AS sub, res.[mode:localName] AS name ");
        jql.append(" FROM [" + FedoraTypes.FEDORA_RESOURCE + "] AS [res] ");
        jql.append(" WHERE ");

        // permission
        // public only
        jql.append(" res.[dcterms:accessRights] = CAST('" + publicAccessRights + "' AS STRING) ");

        // mixin type constraint
        jql.append(" AND res.[" + propHasMixinType + "] = '" + mixinTypes + "'");

        // start datetime constraint
        if (StringUtils.isNotBlank(from)) {
            jql.append(" AND");
            jql.append(" res.[" + propJcrLastModifiedDate + "] >= CAST('" + from + "' AS DATE)");
        }

        // end datetime constraint
        if (StringUtils.isNotBlank(until)) {
            // www.openarchives.org/Register/ValidateSite tests by sending the same YYYY-MM-DDYHH:MM:SSZ
            // (second granularity) as the "from" and "until" values. Fedora uses millisecond granularity
            // the truncation to second granularity causes failures when the "from" and "until are the same
            // thus the comparison fails "from" <= object.timestamp <= "until" fails
            // E.G., 2017-03-13T22:36:23Z <= 2017-03-13T22:36:23.655Z <= 2017-03-13T22:36:23Z fails
            // Fix: add 999 milliseconds to the end of the "until" thus accounting for the second granularity
            DateTime dt = dateFormat.parseDateTime(until);
            dt = dt.plusMillis(999);
            jql.append(" AND");
            jql.append(" res.[" + propJcrLastModifiedDate + "] <= CAST('"
                    + dt.toString(dateFormatMillis) + "' AS DATE)");
        }

        // limit returned hasModel properties
        if (metadataPrefix.equals(METADATA_PREFIX_OAI_ETDMS) || metadataPrefix.equals(METADATA_PREFIX_ORE)) {
            // metadata prefix etdms and ore for thesis only
            jql.append(" AND")
                .append(" res.[" + propHasModel + "] = '").append(modelIRThesis).append("'");
        } else {
            // include both thesis and generic items
            jql.append(" AND (")
                .append(" res.[" + propHasModel + "] = '").append(modelIRThesis).append("'")
                .append(" OR ")
                .append(" res.[" + propHasModel + "] = '").append(modelIRItem).append("'")
                .append(") ");
        }

        // set constraint
        if (StringUtils.isNotBlank(set)) {
            jql.append(" AND");
            jql.append(" res.[" + propHasCollectionId + "] = '"
                    + set + "\u0018^^\u0018http://www.w3.org/2001/XMLSchema#string'");
        }
        if (limit > 0) {
            jql.append(" LIMIT ").append(maxListSize);
            jql.append(" OFFSET ").append(offset);
        }

        log.debug(jql.toString());
        return jql.toString();
    }

    private RowIterator executeQuery(final QueryManager queryManager, final String jql) throws RepositoryException {
        final Stopwatch timer = Stopwatch.createStarted();
        final Query query = queryManager.createQuery(jql, Query.JCR_SQL2);
        final QueryResult results = (QueryResult) query.execute();
        log.debug("query took: " + timer);
        log.debug(jql);
        log.debug(results.getPlan());
        return results.getRows();
    }

    private void validateDateTimeFormat(final String dateTime) {
        if (StringUtils.isNotBlank(dateTime)) {
            dateFormat.parseDateTime(dateTime);
        }
    }

    /**
     * Get a property name for an RDF predicate
     *
     * @param session
     * @param predicate
     * @return property name from the given predicate
     * @throws RepositoryException general repository exception
     */
    private String getPropertyName(final Session session, final Property predicate) throws RepositoryException {

        final NamespaceRegistry namespaceRegistry
                = (org.modeshape.jcr.api.NamespaceRegistry) session.getWorkspace().getNamespaceRegistry();
        final Map<String, String> namespaceMapping = emptyMap();
        return getPropertyNameFromPredicate(namespaceRegistry, predicate, namespaceMapping);
    }

    private String searchResourceQuery(final Session session, final String mixinTypes, final String property,
            final String value, final int limit, final int offset) throws RepositoryException {

        final String propJcrPath = getPropertyName(session, createProperty(RdfJcrLexicon.JCR_NAMESPACE + "path"));
        final String propHasMixinType = getPropertyName(session, RdfJcrLexicon.HAS_MIXIN_TYPE);
        final String propHasModel = getPropertyName(session, createProperty(propertyHasModel));
        final StringBuilder jql = new StringBuilder();
        jql.append("SELECT res.[" + propJcrPath + "] AS sub ");
        jql.append(" FROM [" + FedoraTypes.FEDORA_RESOURCE + "] AS [res] ");
        jql.append(" WHERE ");

        // permission
        // public only
        jql.append(" res.[dcterms:accessRights] = CAST('" + publicAccessRights + "' AS STRING) ");

        // mixin type constraint
        jql.append(" AND res.[" + propHasMixinType + "] = '" + mixinTypes + "'");

        // items
        jql.append(" AND");
        jql.append(" res.[" + propHasModel + "] = '").append(modelIRItem).append("'");

        // search criteria
        jql.append(" AND");
        jql.append(" res.[").append(property).append("] LIKE '%").append(value).append("%'");

        // limit and offset
        if (limit > 0) {
            jql.append(" LIMIT ").append(maxListSize);
            jql.append(" OFFSET ").append(offset);
        }

        log.debug(jql.toString());
        return jql.toString();
    }

    /**
     * Search records.
     *
     * @param session the session
     * @param uriInfo the uri info
     * @param metadataPrefix the metadata prefix
     * @param property query property
     * @param value value of the query property
     * @param offset query offset
     * @return the jAXB element
     * @throws RepositoryException the repository exception
     */
    public JAXBElement<OAIPMHtype> search(final Session session, final UriInfo uriInfo, final String metadataPrefix,
            final String property, final String value, final int offset) throws RepositoryException {

        final FedoraSession fedoraSession = sessionFactory.getInternalSession();
        final HttpSession httpSession = new HttpSession(fedoraSession);

        if (!searchEnabled) {
            return error(VerbType.LIST_RECORDS, null, null, OAIPMHerrorcodeType.NO_SET_HIERARCHY,
                    "Search is not enabled");
        }

        final HttpResourceConverter converter
                = new HttpResourceConverter(httpSession, uriInfo.getBaseUriBuilder().clone().path(FedoraNodes.class));
        final ValueConverter valueConverter = new ValueConverter(session, converter);

        if (metadataPrefix == null) {
            return error(VerbType.LIST_RECORDS, null, null, OAIPMHerrorcodeType.BAD_ARGUMENT,
                    "metadataprefix is invalid");
        }
        final MetadataFormat mdf = metadataFormats.get(metadataPrefix);
        if (mdf == null) {
            return error(VerbType.LIST_RECORDS, null, metadataPrefix, OAIPMHerrorcodeType.CANNOT_DISSEMINATE_FORMAT,
                    "Unavailable metadata format");
        }

        final String jql
                = searchResourceQuery(session, FedoraTypes.FEDORA_CONTAINER, property, value, maxListSize, offset);
        try {

            final QueryManager queryManager = session.getWorkspace().getQueryManager();
            final RowIterator result = executeQuery(queryManager, jql);

            if (!result.hasNext()) {
                return error(VerbType.LIST_RECORDS, null, metadataPrefix, OAIPMHerrorcodeType.NO_RECORDS_MATCH,
                        "No record found");
            }

            final OAIPMHtype oai = oaiFactory.createOAIPMHtype();
            oai.setResponseDate(dataFactory.newXMLGregorianCalendar(dateFormat.print(new Date().getTime())));
            final ListRecordsType records = oaiFactory.createListRecordsType();
            while (result.hasNext()) {
                final Row solution = result.nextRow();
                final Resource subjectUri = valueConverter.convert(solution.getValue("sub")).asResource();
                final String name = valueConverter.convert(solution.getValue("name")).asLiteral().getString();
                final RecordType record = createRecord(session, mdf, converter.asString(subjectUri), name, uriInfo);
                records.getRecord().add(record);
            }

            final RequestType req = oaiFactory.createRequestType();
            if (records.getRecord().size() == maxListSize) {
                final ResumptionTokenType token = oaiFactory.createResumptionTokenType();
                token.setValue(encodeResumptionToken(VerbType.LIST_RECORDS.value(), metadataPrefix, null, null, null,
                        offset + maxListSize));
                token.setCursor(new BigInteger(String.valueOf(offset)));
                token.setCompleteListSize(new BigInteger(String.valueOf(result.getSize() + offset)));
                records.setResumptionToken(token);
            }
            req.setVerb(VerbType.LIST_RECORDS);
            req.setMetadataPrefix(metadataPrefix);
            req.setFrom(null);
            req.setUntil(null);
            req.setSet(null);
            oai.setRequest(req);
            oai.setListRecords(records);
            return oaiFactory.createOAIPMH(oai);
        } catch (final Exception e) {
            e.printStackTrace();
            throw new RepositoryException(e);
        }
    }

    /**
     * The delete method.
     *
     * @param path to the container
     * @return jaxb element in the OAI-PMH Type as the root
     * @throws RepositoryException general repository exception
     */
    public JAXBElement<OAIPMHtype> delete(final String path) throws RepositoryException {
        final FedoraSession session = sessionFactory.getInternalSession();

        try {
            if (nodeService.exists(session, path)) {
                log.trace("Deleting resource {} ...", path);
                final Container con = containerService.find(session, path);
                con.delete();
                session.commit();
                log.trace("Resource {} has been deleted", path);
                return error(VerbType.GET_RECORD, null, null, OAIPMHerrorcodeType.ID_DOES_NOT_EXIST,
                        "Resource has been deleted");
            }
            log.trace("Resource {} does not exist", path);
            return error(VerbType.GET_RECORD, null, null, OAIPMHerrorcodeType.ID_DOES_NOT_EXIST,
                    "Resource does not exist");
        } catch (final Exception e) {
            log.trace("Delete resource error!", e);
            return error(VerbType.GET_RECORD, null, null, OAIPMHerrorcodeType.ID_DOES_NOT_EXIST, e.getMessage());
        }
    }

    /**
     * get path given an noid identifier vai a jcr query
     *
     * @param session the session
     * @param noid identifier
     * @param metadataPrefix string indicating the type of metadata
     *
     * @return Fedora path or null if identifier not found
     * @throws RepositoryException the repository exception
     */
    protected String getPathFromNoid(final Session session, final String noid, final String metadataPrefix)
            throws RepositoryException {
        String path = null;
        if (noid != null) {
            final StringBuilder jql = new StringBuilder();
            jql.append("SELECT res.[jcr:path] AS path ");
            jql.append(" FROM [fedora:Resource] AS res ");
            jql.append(" WHERE ");

            jql.append(" res.[mode:localName] = '").append(noid).append("'");

            // permission
            // public only
            jql.append(" AND res.[dcterms:accessRights] = CAST('" + publicAccessRights + "' AS STRING)");
            // limit returned hasModel properties
            if (metadataPrefix != null
                && (metadataPrefix.equals(METADATA_PREFIX_OAI_ETDMS) || metadataPrefix.equals(METADATA_PREFIX_ORE))
                ) {
                // metadata prefix etdms and ore for thesis only
                jql.append(" AND")
                    .append(" res.[model:hasModel] = '").append(modelIRThesis).append("'");
            } else {
                // include both thesis and generic items
                jql.append(" AND (")
                    .append(" res.[model:hasModel] = '").append(modelIRThesis).append("'")
                    .append(" OR ")
                    .append(" res.[model:hasModel] = '").append(modelIRItem).append("'")
                    .append(") ");
            }

            log.debug(jql.toString());

            final QueryManager queryManager = session.getWorkspace().getQueryManager();
            final RowIterator result = executeQuery(queryManager, jql.toString());

            if (result.hasNext()) {
                path = result.nextRow().getValue("path").getString();
            }
        }

        return path;
    }

    /**
     * get noid from identifier.
     *
     * @param identifier id passed (e.g., oai:era.library.ualberta.ca:1/)
     *
     * @return noid String or null if not valid
     * @throws Exception the exception for a malformed identifier
     */
    protected String getNoidFromIdentifier(final String identifier) throws Exception {
        // whitelist noid to avoid JCR injections
        final String noid = slashPattern.split(identifier)[1];
        return noid.matches("^[/\\pL\\pN:_-]+$") ? noid : null;
    }

    /**
     * Get the filename property from the PCDM model within Fcrepo
     *
     * @param obj the object container
     * @param converter an HttpresoruceConverter
     * @param fedoraSession current session object
     *
     * @return a list of container object representing "Original files": http://pcdm.org/use#OriginalFile
     */
     public final List<FedoraBinary> getFileResources(
             final Container obj, final HttpResourceConverter converter,
             final FedoraSession fedoraSession
     ) {

         final List<FedoraBinary> fileObjList = new ArrayList<FedoraBinary>();

        Stream<Triple> proxyTriples;
        Stream<Triple> fileSetTriples;

        final java.util.function.Predicate<Triple> hasMemberPredicate
                = new PropertyPredicate("http://pcdm.org/models#hasMember");
        final java.util.function.Predicate<Triple> hasFilePredicate
                = new PropertyPredicate("http://pcdm.org/models#hasFile");

        // lookup the dcdm:hasMember to get the FileSet proxy
        proxyTriples = obj.getTriples(converter, RequiredRdfContext.LDP_MEMBERSHIP).filter(hasMemberPredicate);
        final List<Triple> tripleList = proxyTriples.collect(toList());

        for (Triple triple : tripleList) {
            try {
                // convert URI into a fcrepo path for JCR node query and lookup proxy
                final String proxyPath = (new URI(triple.getObject().getURI())).toString();
                // extract the path from the full uri
                final Container proxyObj
                        = containerService.find(
                                fedoraSession,"/" + proxyPath.replace(converter.toDomain("").toString(),""));
                // find the pcdm:hasFile predicate
                fileSetTriples =
                        proxyObj.getTriples(converter, RequiredRdfContext.LDP_MEMBERSHIP).filter(hasFilePredicate);
                final List<Triple> hasFileList = fileSetTriples.collect(toList());

                for ( Triple hasFileTriple : hasFileList) {
                    // for each file, add object to list
                    log.debug(hasFileTriple.toString());
                    final String tmp = hasFileTriple.getObject().getURI();
                    // extract the path from the full uri
                    final String filePath = "/" + tmp.replace(converter.toDomain("").toString(),"");
                    // treat as a FedoraBinary and find via binaryService otherwise missing FileSet properties
                    // like ebucore:filename, premise:hasSize
                    final FedoraBinary fedoraBinaryObject = binaryService.find(fedoraSession, filePath);
                    if (fedoraBinaryObject != null ) {
                        log.debug(fedoraBinaryObject.toString());
                        if (fedoraBinaryObject.getTypes().contains(new URI("http://pcdm.org/use#OriginalFile"))) {
                            fileObjList.add(fedoraBinaryObject);
                        }
                    }
                }
            } catch (Exception e) {
                log.error(e.toString());
            }
        }
        return fileObjList;
     }

    /**
     * Sets property has set spec.
     *
     * @param propertyHasSetSpec the property has set spec
     */
    public void setPropertyHasSetSpec(final String propertyHasSetSpec) {
    }

    /**
     * Sets property set name.
     *
     * @param propertySetName the property set name
     */
    public void setPropertySetName(final String propertySetName) {
    }

    /**
     * Sets property has sets.
     *
     * @param propertyHasSets the property has sets
     */
    public void setPropertyHasSets(final String propertyHasSets) {
    }

    /**
     * The setPropertyHasModel setter method.
     *
     * @param propertyHasModel the propertyHasModel to set
     */
    public void setPropertyHasModel(final String propertyHasModel) {
        this.propertyHasModel = propertyHasModel;
    }

    /**
     * The setIdFormat setter method.
     *
     * @param idFormat the idFormat to set
     */
    public final void setIdFormat(final String idFormat) {
        this.idFormat = idFormat;
    }

    /**
     * Sets max list size.
     *
     * @param maxListSize the max list size
     */
    public void setMaxListSize(final int maxListSize) {
        this.maxListSize = maxListSize;
    }

    /**
     * Sets property is part of set.
     *
     * @param propertyHasCollectionId the property has colletion id
     */
    public void setPropertyHasCollectionId(final String propertyHasCollectionId) {
        this.propertyHasCollectionId = propertyHasCollectionId;
    }

    /**
     * Set propertyOaiRepositoryName
     *
     * @param propertyOaiRepositoryName the oai repository name
     */
    public void setPropertyOaiRepositoryName(final String propertyOaiRepositoryName) {
        this.propertyOaiRepositoryName = propertyOaiRepositoryName;
    }

    /**
     * Set propertyOaiDescription
     *
     * @param propertyOaiDescription the oai description
     */
    public void setPropertyOaiDescription(final String propertyOaiDescription) {
        this.propertyOaiDescription = propertyOaiDescription;
    }

    /**
     * Set propertyOaiAdminEmail
     *
     * @param propertyOaiAdminEmail the oai admin email
     */
    public void setPropertyOaiAdminEmail(final String propertyOaiAdminEmail) {
        this.propertyOaiAdminEmail = propertyOaiAdminEmail;
    }

    /**
     * The setNamespaces setter method.
     *
     * @param namespaces the namespaces to set
     */
    public void setNamespaces(final Map<String, String> namespaces) {
        this.namespaces = namespaces;
    }

    /**
     * Sets sets root path.
     *
     * @param rootPath the oai root path
     */
    public void setRootPath(final String rootPath) {
        this.rootPath = rootPath;
    }

    /**
     * Sets sets enabled.
     *
     * @param setsEnabled the sets enabled
     */
    public void setSetsEnabled(final boolean setsEnabled) {
        this.setsEnabled = setsEnabled;
    }

    /**
     * Sets metadata for
     *
     * @param metadataFormats the metadata formats
     */
    public void setMetadataFormats(final Map<String, MetadataFormat> metadataFormats) {
        this.metadataFormats = metadataFormats;
    }

    /**
     * Sets descriptive content.
     *
     * @param descriptiveContent the descriptive content
     */
    public void setDescriptiveContent(final Map<String, String> descriptiveContent) {
        this.descriptiveContent = descriptiveContent;
    }

    /**
     * The setPropertyOaiBaseUrl setter method.
     *
     * @param propertyOaiBaseUrl the propertyOaiBaseUrl to set
     */
    public void setPropertyOaiBaseUrl(final String propertyOaiBaseUrl) {
        this.propertyOaiBaseUrl = propertyOaiBaseUrl;
    }

    /**
     * The setSearchEnabled setter method.
     *
     * @param searchEnabled the searchEnabled to set
     */
    public void setSearchEnabled(final boolean searchEnabled) {
        this.searchEnabled = searchEnabled;
    }

}
