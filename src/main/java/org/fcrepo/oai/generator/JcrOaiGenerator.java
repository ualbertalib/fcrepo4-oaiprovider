/**
 * Piyapong Charoenwattana
 * Project: fcrepo-oaiprovider
 * $Id$
 */

package org.fcrepo.oai.generator;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

/**
 * The JcrOaiGenerator class.
 *
 * @author <a href="mailto:piyapong.charoenwattana@gmail.com">Piyapong Charoenwattana</a>
 * @version $Revision$ $Date$
 */
public class JcrOaiGenerator {

    protected String eraIdFormat;
    protected String doiFullUriFormat;
    protected Map<String, String> institutionMap;
    protected Map<String, String> jcrNamespaceMap;
    protected Map<String, String> dcTypeMap;
    protected Set<String> dcTypeExclusionsSet;


    /**
     * The setEraIdFormat setter method.
     *
     * @param eraIdFormat the eraIdFormat to set
     */
    public final void setEraIdFormat(final String eraIdFormat) {
        this.eraIdFormat = eraIdFormat;
    }

    /**
     * The setFullDoiUrl setter method.
     *
     * @param s the DIO full uri format
     */
    public final void setDoiFullUriFormat(final String s) {
        this.doiFullUriFormat = s;
    }

    /**
     * Given the JCR DOI (doi:10.7939/FEXDSF) convert to the full DOI URI https://doi.org/10.7939/FEXDSF
     *
     * @param ualibDoi the ualid:doi field within JCR
     * @return the full DOI uri
     */
    protected final String formatUalidDoi(final String ualibDoi) {
        String fullDoiUrl = null;
        if (StringUtils.isNotEmpty(ualibDoi) && ualibDoi.length() >= 4) {
            fullDoiUrl = String.format(doiFullUriFormat, ualibDoi.substring(4));
        }
        return fullDoiUrl;
    }

    /**
     * given a FedoraBinary file path, return the PCDM fileSet portion
     * e.g., /dev/e9/87/8b/5e/e9878b5e-354f-4f77-b3b2-6caa0175b656/files/1bece7c9-d7d8-4d17-967e-d9836113ff0c
     * grab "e9878b5e-354f-4f77-b3b2-6caa0175b656"
     *
     * @param path FedoraBinary file path
     * @return String representing the FileSet UUID
     */
    protected final String getFileSetFromPath(final String path) {
        String ret = "";
        final Pattern fileSetPattern = Pattern.compile(".*\\/([^/]*)\\/files\\/.*");
        final Matcher matcher = fileSetPattern.matcher(path);
        while (matcher.find()) {
            ret = matcher.group(1);
        }
        return ret;
    }

    /**
     * The Institution Controlled Vocabulary setter method.
     *
     * @param controlledVocabularyInstitutionMap the institution map to convert URIs to human readable strings.
     */
    public void setControlledVocabularyInstitutionMap(final Map<String, String> controlledVocabularyInstitutionMap) {
        this.institutionMap = controlledVocabularyInstitutionMap;
    }


    /**
     * The JCR Namespace Map setter method.
     *
     * @param jcrNamespaceMap a Map obect containing a list of JCR syntax namespace.
     */
    public void setJcrNamespaceMap(final Map<String, String> jcrNamespaceMap) {
        this.jcrNamespaceMap = jcrNamespaceMap;
    }

    /**
     * dc:type url to literal mapping
     *
     * @param dcTypeMap a Map object containing the url to literal mapping
     */
    public void setDcTypeMap(final Map<String, String> dcTypeMap) {
      this.dcTypeMap = dcTypeMap;
    }

    /**
     * dc:type urls to exclude
     *
     * @param dcTypeExclusionsSet a set object containing urls to exclude
     */
    public void setDcTypeExclusionsSet(final Set<String> dcTypeExclusionsSet) {
      this.dcTypeExclusionsSet = dcTypeExclusionsSet;
    }

    /**
     * dc:type: map to literal value from dcTypeMap and dcTypeExclusions
     *
     * @param input a dc:type value string
     * @return literal (not url) value or null if not in dcTypeMap or an exclusion
     */
    public String getDcTypeValue(final String input) {
      String ret = null;
      if (input != null) {
        if (!this.dcTypeExclusionsSet.contains(input)) {
          ret = this.dcTypeMap.get(input);
        }
      }
      return ret;
    }

}
