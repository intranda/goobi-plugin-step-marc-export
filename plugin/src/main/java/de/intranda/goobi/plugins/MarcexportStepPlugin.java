/**
 * This file is part of a plugin for Goobi - a Workflow tool for the support of mass digitization.
 *
 * Visit the websites for more information.
 *          - https://goobi.io
 *          - https://www.intranda.com
 *          - https://github.com/intranda/goobi
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

package de.intranda.goobi.plugins;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.lang3.StringUtils;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.StorageProviderInterface;
import de.sub.goobi.helper.exceptions.SwapException;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.Corporate;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataGroup;
import ugh.dl.MetadataGroupType;
import ugh.dl.MetadataType;
import ugh.dl.Person;
import ugh.dl.Prefs;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.fileformats.mets.MetsModsImportExport;

@PluginImplementation
@Log4j2
public class MarcexportStepPlugin implements IStepPluginVersion2 {

    private static final long serialVersionUID = -8330640131681090785L;
    @Getter
    private String title = "intranda_step_marcexport";
    @Getter
    private Step step;
    @Getter
    private String returnPath;

    private static final Namespace marc = Namespace.getNamespace("marc", "http://www.loc.gov/MARC21/slim");

    private static final String SUBFIELD_NAME = "subfield";
    private static final String CONTROLFIELD_NAME = "controlfield";

    private static final StorageProviderInterface storageProvider = StorageProvider.getInstance();

    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("[^A-Za-z0-9]");

    private transient List<MarcMetadataField> marcFields = new ArrayList<>();
    private transient List<MarcDocstructField> docstructFields = new ArrayList<>();

    private static final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

    private String exportFolder;

    @Override
    public void initialize(Step step, String returnPath) {
        this.returnPath = returnPath;
        this.step = step;

        SubnodeConfiguration myconfig = ConfigPlugins.getProjectAndStepConfig(title, step);
        myconfig.setExpressionEngine(new XPathExpressionEngine());

        exportFolder = myconfig.getString("/exportFolder");
        if (!exportFolder.endsWith("/")) {
            exportFolder = exportFolder + "/";
        }

        List<HierarchicalConfiguration> hcl = myconfig.configurationsAt("/marcField");
        for (HierarchicalConfiguration hc : hcl) {
            String type = hc.getString("@type", "datafield");
            String mainTag = hc.getString("@mainTag");
            String ind1 = hc.getString("@ind1").replace("_", " ");
            String ind2 = hc.getString("@ind2").replace("_", " ");
            String subTag = hc.getString("@subTag");
            String repetitionMode = hc.getString("@reuseMode", "none");
            String rulesetName = hc.getString("@rulesetName");
            String additionalSubFieldCode = hc.getString("@additionalSubFieldCode");
            String additionalSubFieldValue = hc.getString("@additionalSubFieldValue");
            boolean anchorMetadata = hc.getBoolean("@anchorMetadata", false);
            String conditionField = hc.getString("@conditionField", null);
            String conditionValue = hc.getString("@conditionValue", null);
            String conditionType = hc.getString("@conditionType", "is");
            String text = hc.getString("@text", "");
            String wrapperLeft = hc.getString("@wrapperLeft", null);
            String wrapperRight = hc.getString("@wrapperRight", null);
            String patternTemplate = hc.getString("@patternTemplate", "");
            String patternTarget = hc.getString("@patternTarget", "");
            String mergeSeparator = hc.getString("@mergeSeparator", null);
            String regularExpression = hc.getString("@regularExpression", null);
            MarcMetadataField mmf = new MarcMetadataField(type, mainTag, ind1, ind2, subTag, repetitionMode, rulesetName, additionalSubFieldCode,
                    additionalSubFieldValue, anchorMetadata, conditionField, conditionValue, conditionType, text, wrapperLeft, wrapperRight,
                    patternTemplate, patternTarget, mergeSeparator, regularExpression);
            marcFields.add(mmf);
        }

        hcl = myconfig.configurationsAt("/doctype");
        for (HierarchicalConfiguration hc : hcl) {
            boolean exportDocstruct = hc.getBoolean("@export");
            String docstructName = hc.getString("@rulesetName");
            String leader6 = hc.getString("@leader6");
            String leader7 = hc.getString("@leader7");
            String leader19 = hc.getString("@leader19");
            String dependencyType = hc.getString("@dependencyType");
            String dependencyMetadata = hc.getString("@dependencyMetadata");
            String dependencyValue = hc.getString("@dependencyValue");
            docstructFields.add(new MarcDocstructField(exportDocstruct, docstructName, leader6, leader7, leader19, dependencyType, dependencyMetadata,
                    dependencyValue));
        }
    }

    @Override
    public PluginGuiType getPluginGuiType() {
        return PluginGuiType.NONE;
    }

    @Override
    public String getPagePath() {
        return null;
    }

    @Override
    public PluginType getType() {
        return PluginType.Step;
    }

    @Override
    public String cancel() {
        return "/uii" + returnPath;
    }

    @Override
    public String finish() {
        return "/uii" + returnPath;
    }

    @Override
    public int getInterfaceVersion() {
        return 0;
    }

    @Override
    public HashMap<String, StepReturnValue> validate() {
        return new HashMap<>();
    }

    @Override
    public boolean execute() {
        PluginReturnValue ret = run();
        return ret != PluginReturnValue.ERROR;
    }

    @Override
    public PluginReturnValue run() {
        Prefs prefs = step.getProzess().getRegelsatz().getPreferences();

        List<DocStruct> docstructList = prepareDocStructList();
        if (docstructList == null) {
            // error happened
            return PluginReturnValue.ERROR;
        }

        for (DocStruct docstruct : docstructList) {
            // 1. get identifier
            String identifier = getIdentifierOfDocStruct(docstruct);

            // 2. get currentField
            MarcDocstructField currentField = getCurrentMarcField(docstruct);

            // 3. check if exportable
            boolean exportable = isDocStructExportable(docstruct, identifier, currentField);

            // 4. use results from 1, 2, 3 to control whether to go further, hence 1 - 4 are just preparation steps
            if (!exportable) {
                log.debug("docstruct is not exportable");
                continue;
            }

            // 5. start to prepare the MARC document
            Document marcDoc = new Document();
            Element recordElement = new Element("record", marc);
            marcDoc.setRootElement(recordElement);

            Element leaderElement = new Element("leader", marc);
            StringBuilder leader = createLeader(currentField);
            leaderElement.setText(leader.toString());
            recordElement.addContent(leaderElement);

            Element marcField = null;
            Metadata firstPersonOrCorporate = null;
            // there should be ONLY ONE main entry, either Person or Corporate, but NOT both
            boolean firstPersonOrCorporateWritten = false;
            for (MarcMetadataField configuredField : marcFields) {
                String type = configuredField.getRulesetName();
                // condition type
                MetadataType conditionType = null;
                if (StringUtils.isNotBlank(configuredField.getConditionField())) {
                    conditionType = prefs.getMetadataTypeByName(configuredField.getConditionField());
                }

                /*
                 * REMARKS ON MARC:
                 *      1. Fields beginning with a 1 are referred to as main entry fields, and there should be ONLY ONE of them in each MARC record.
                 *      1.1. Person - 100
                 *      1.2. Corporate - 110
                 * 
                 *      2. Fields beginning with a 7 are used to provide additional access to the manifestation being cataloged.
                 *      2.1. Person - 700
                 *      2.2. Corporate - 710
                 */
                // write metadata according to actual types
                if (type == null) {
                    // static text, not metadata
                    marcField = writeMetadataGeneral(docstruct, recordElement, marcField, configuredField, null, conditionType);
                } else if ("group".equals(configuredField.getReuseMode())) {
                    // metadata groups
                    MetadataGroupType mgt = prefs.getMetadataGroupTypeByName(type);
                    List<MetadataGroup> grps = docstruct.getAllMetadataGroupsByType(mgt);
                    for (MetadataGroup grp : grps) {
                        // generate new main field
                        marcField = createMainElement(recordElement, configuredField);

                        for (Metadata md : grp.getMetadataList()) {
                            MetadataType mdt = md.getType();
                            // find mapping for
                            String groupFieldName = mgt.getName() + "/" + mdt.getName();
                            // find matching export mapping

                            for (MarcMetadataField field : marcFields) {
                                if (StringUtils.isNotBlank(field.getRulesetName()) && field.getRulesetName().equals(groupFieldName)) {
                                    marcField = writeMetadataGeneral(docstruct, recordElement, marcField, field, md, conditionType);
                                }

                            }

                        }
                    }

                } else {
                    MetadataType mdt = prefs.getMetadataTypeByName(type);
                    if (mdt != null) {
                        List<? extends Metadata> list = getMetadataListGeneral(docstruct, configuredField, mdt);

                        if (list != null) {
                            for (Metadata md : list) {
                                // check if we should call writeMetadataGeneral, which depends on mdt
                                int writeCode = getMetadataWriteCode(configuredField, mdt, firstPersonOrCorporate, md, firstPersonOrCorporateWritten);
                                if (writeCode < 0) {
                                    continue;
                                }

                                marcField = writeMetadataGeneral(docstruct, recordElement, marcField, configuredField, md, conditionType);

                                if (writeCode == 100 || writeCode == 110 || writeCode == 111 || writeCode == 130) {
                                    // first Person or first Corporate found
                                    firstPersonOrCorporate = md;
                                    firstPersonOrCorporateWritten = true;
                                }
                            }
                        }
                    }
                }
            }

            // 6. save the MARC file
            XMLOutputter out = new XMLOutputter();
            out.setFormat(Format.getPrettyFormat());
            try {
                Path outputFolder = Paths.get(exportFolder, "" + step.getProzess().getId());
                if (!storageProvider.isDirectory(outputFolder)) {
                    storageProvider.createDirectories(outputFolder);
                }
                out.output(marcDoc, new FileOutputStream(exportFolder + step.getProzess().getId() + "/" + identifier + ".xml"));
            } catch (IOException e) {
                log.error(e);
                return PluginReturnValue.ERROR;
            }
        }
        log.info("Marcexport step plugin executed");

        return PluginReturnValue.FINISH;
    }

    private List<DocStruct> prepareDocStructList() {
        Fileformat ff = getFileformat(); // only used to create the docstructList
        if (ff == null) {
            // log error message
            return null; // NOSONAR
        }

        try {
            DocStruct docstruct = ff.getDigitalDocument().getLogicalDocStruct();

            if (!isIdentifierExistsInDocStruct(docstruct)) {
                Helper.setFehlerMeldung("Missing identifier metadata");
                return null; // NOSONAR
            }

            List<DocStruct> docstructList = new ArrayList<>();

            docstructList.add(docstruct);
            if (docstruct.getType().isAnchor()) {
                docstructList.add(docstruct.getAllChildren().get(0));
            }

            return docstructList;

        } catch (PreferencesException e1) {
            log.error(e1);
            return null; // NOSONAR
        }
    }

    private boolean isIdentifierExistsInDocStruct(DocStruct docstruct) {
        for (Metadata md : docstruct.getAllMetadata()) {
            if (md.getType().isIdentifier()) {
                return true;
            }
        }

        return false;
    }

    private String getIdentifierOfDocStruct(DocStruct docstruct) {
        String identifier = null;

        List<Metadata> identifierList = docstruct.getAllIdentifierMetadata();
        if (identifierList != null) {
            identifier = identifierList.get(0).getValue();
        }

        return identifier;
    }

    private MarcDocstructField getCurrentMarcField(DocStruct docstruct) {
        String typeName = docstruct.getType().getName();
        for (MarcDocstructField field : docstructFields) {
            if (typeName.equals(field.getDocstructName())) {
                return field;
            }
        }

        return null;
    }

    private boolean isDocStructExportable(DocStruct docstruct, String identifier, MarcDocstructField currentField) {
        return StringUtils.isNotBlank(identifier) && currentField != null && currentField.isExportDocstruct()
                && isFieldDependencyFulfilled(docstruct, currentField);
    }

    private boolean isFieldDependencyFulfilled(DocStruct docstruct, MarcDocstructField currentField) {
        if (StringUtils.isBlank(currentField.getDependencyType())) {
            return true;
        }

        DocStruct dsToCheck = null;
        if (docstruct.getType().isAnchor()) {
            if ("anchor".equals(currentField.getDependencyType())) {
                dsToCheck = docstruct;
            } else {
                dsToCheck = docstruct.getAllChildren().get(0);
            }
        } else if ("anchor".equals(currentField.getDependencyType())) {
            dsToCheck = docstruct.getParent();
        } else {
            dsToCheck = docstruct;
        }

        String dependencyType = currentField.getDependencyMetadata();
        String dependencyValue = currentField.getDependencyValue();
        for (Metadata md : dsToCheck.getAllMetadata()) {
            if (md.getType().getName().equals(dependencyType) && md.getValue().equals(dependencyValue)) {
                // metadata found and its value matches
                return true;
            }
        }

        return false;
    }

    private int getMetadataWriteCode(MarcMetadataField configuredField, MetadataType mdt, Metadata firstMetadata, Metadata currentMetadata,
            boolean firstPersonOrCorporateWritten) {
        String marcMainTag = configuredField.getMarcMainTag();
        switch (marcMainTag) {
            case "100":
            case "110":
            case "111":
            case "130":
            case "700":
            case "710":
            case "711":
            case "730":
                if (firstPersonOrCorporateWritten) {
                    boolean sameFirst = firstMetadata != null;
                    if (mdt.getIsPerson()) {
                        sameFirst = sameFirst && firstMetadata instanceof Person && ((Person) firstMetadata).equals((Person) currentMetadata);
                    } else if (mdt.isCorporate()) {
                        sameFirst = sameFirst && firstMetadata instanceof Corporate && ((Corporate) firstMetadata).equals(currentMetadata);
                    }
                    // -1 means that this Metadata should not be written
                    // 710 means that this is an additional Corporate and can be written
                    // 700 means that this is an additional Person and can be written
                    if (sameFirst) {
                        return -1;
                    } else if ("700".equals(marcMainTag)) {
                        return 700;
                    } else if ("710".equals(marcMainTag)) {
                        return 710;
                    } else if ("711".equals(marcMainTag)) {
                        return 711;
                    } else if ("730".equals(marcMainTag)) {
                        return 730;
                    } else {
                        return -1;
                    }
                }

                // 110 means that this is the first Corporate and can be written
                // 100 means that this is the first Person and can be written

                if ("100".equals(marcMainTag)) {
                    return 100;
                } else if ("110".equals(marcMainTag)) {
                    return 110;
                } else if ("111".equals(marcMainTag)) {
                    return 111;
                } else if ("130".equals(marcMainTag)) {
                    return 130;
                } else {
                    return -1;
                }
            default:
                return 0;

        }
    }

    private Element writeMetadataGeneral(DocStruct docstruct, Element recordElement, Element marcField, MarcMetadataField configuredField,
            Metadata md, MetadataType conditionType) {
        // configured condition, check if they match
        if (conditionType != null) {
            boolean match = checkConditions(docstruct, configuredField, conditionType);
            if (!match) {
                return marcField;
            }
        }

        marcField = generateMarcField(recordElement, marcField, configuredField);

        String marcFieldText = getWrappedMarcFieldText(configuredField, md);
        Element elementToSetText;
        String mergeSeparator = configuredField.getMergeSeparator();
        // The controlfield-check was not there for Person and Corporation, but I think it should be. - Zehong
        if (mergeSeparator != null && (elementToSetText = getElementToSetText(marcField, configuredField)) != null) {

            String mergedText = getMergedText(elementToSetText.getText(), mergeSeparator, marcFieldText);
            elementToSetText.setText(mergedText);

        } else if (CONTROLFIELD_NAME.equals(configuredField.getFieldType())) {
            marcField.setText(marcFieldText);

        } else { // no need to merge or there is still no proper subfield available yet
            Element subfield = new Element(SUBFIELD_NAME, marc);
            subfield.setAttribute("code", configuredField.getMarcSubTag());
            subfield.setText(marcFieldText);
            marcField.addContent(subfield);

            // The following X-check block was not there for Person and Corporation, but I think it should be. - Zehong
            if ("X".equals(marcField.getAttributeValue("ind2"))) {
                // sorting title
                int ind2Value = getSortingTitleNumber(marcFieldText);
                marcField.setAttribute("ind2", "" + ind2Value);
            }
        }

        // additional subfield
        if (StringUtils.isNotBlank(configuredField.getAdditionalSubFieldCode())) {
            Element subfield = new Element(SUBFIELD_NAME, marc);
            subfield.setAttribute("code", configuredField.getAdditionalSubFieldCode());
            subfield.setText(configuredField.getAdditionalSubFieldValue());
            marcField.addContent(subfield);
        }

        return marcField;
    }

    private Element getElementToSetText(Element marcField, MarcMetadataField configuredField) {
        if (CONTROLFIELD_NAME.equals(configuredField.getFieldType())) {
            return marcField;
        }

        List<Element> elements = marcField.getChildren();
        return elements.isEmpty() ? null : elements.get(elements.size() - 1);
    }

    private String getMergedText(String oldText, String separator, String newText) {
        if (StringUtils.isBlank(oldText)) {
            return newText;
        }

        return oldText + (StringUtils.isBlank(separator) ? " " : separator) + newText;
    }

    private List<? extends Metadata> getMetadataListGeneral(DocStruct docstruct, MarcMetadataField configuredField, MetadataType mdt) {
        // Person
        if (mdt.getIsPerson()) {
            return docstruct.getAllPersonsByType(mdt);
        }

        // Corporate
        if (mdt.isCorporate()) {
            return docstruct.getAllCorporatesByType(mdt);
        }

        // other Metadata, which is not anchor
        if (!configuredField.isAnchorMetadata()) {
            return docstruct.getAllMetadataByType(mdt);
        }

        // anchor metadata
        if (docstruct.getParent() != null) {
            return docstruct.getParent().getAllMetadataByType(mdt);
        }

        return Collections.emptyList();
    }

    private String getMarcFieldTextFromMetadata(Metadata md) {
        if (md instanceof Corporate) {
            return ((Corporate) md).getMainName();
        }

        if (md instanceof Person) {
            Person p = (Person) md;
            return p.getLastname() + ", " + p.getFirstname();
        }

        // normal metadata
        return md.getValue();
    }

    private Element generateMarcField(Element recordElement, Element marcField, MarcMetadataField configuredField) {
        if ("none".equals(configuredField.getReuseMode())) {
            marcField = createMainElement(recordElement, configuredField);

        } else if (isMarcFieldReusable(marcField, configuredField)) {
            // re-use field

        } else {
            marcField = createMainElement(recordElement, configuredField);
        }

        return marcField;
    }

    private boolean isMarcFieldReusable(Element marcField, MarcMetadataField configuredField) {
        return CONTROLFIELD_NAME.equals(configuredField.getFieldType()) ? isMarcControlFieldReusable(marcField, configuredField)
                : isMarcDataFieldReusable(marcField, configuredField);
    }

    private boolean isMarcControlFieldReusable(Element marcField, MarcMetadataField configuredField) {
        return marcField != null && marcField.getAttributeValue("tag").equals(configuredField.getMarcMainTag());
    }

    private boolean isMarcDataFieldReusable(Element marcField, MarcMetadataField configuredField) {
        return marcField != null && marcField.getAttributeValue("tag").equals(configuredField.getMarcMainTag())
                && configuredField.getInd1().equals(marcField.getAttributeValue("ind1"))
                && ("X".equals(configuredField.getInd2()) || configuredField.getInd2().equals(marcField.getAttributeValue("ind2")));
    }

    private String getWrappedMarcFieldText(MarcMetadataField configuredField, Metadata md) {
        String marcFieldText = md == null ? configuredField.getStaticText() : getMarcFieldTextFromMetadata(md);
        if ("{date}".equals(marcFieldText)) {
            marcFieldText = formatter.format(new Date());
        }
        // check pattern
        if (StringUtils.isNoneBlank(configuredField.getPatternTemplate(), configuredField.getPatternTarget())) {
            marcFieldText = getPatternTargetFromText(marcFieldText, configuredField.getPatternTemplate(), configuredField.getPatternTarget());
        }

        if (StringUtils.isNotBlank(configuredField.getRegularExpression())) {
            List<String> params = MetsModsImportExport.splitRegularExpression(configuredField.getRegularExpression());
            marcFieldText = marcFieldText.replaceAll(params.get(0), params.get(1));
        }

        String left = configuredField.getWrapperLeft();
        String right = configuredField.getWrapperRight();
        if (left == null) {
            left = "";
        } else if (StringUtils.isEmpty(left)) {
            left = " ";
        }
        if (right == null) {
            right = "";
        } else if (StringUtils.isEmpty(right)) {
            right = " ";
        }

        return left + marcFieldText + right;
    }

    private String getPatternTargetFromText(String text, String template, String target) {
        String textCopy = text;

        Matcher matcher = SEPARATOR_PATTERN.matcher(template);
        while (matcher.find()) {
            String separator = matcher.group();
            int splitIndexTemplate = template.indexOf(separator);
            int splitIndexText = textCopy.indexOf(separator);
            if (splitIndexText < 0) {
                // pattern template is not correctly configured
                log.error("Unknown separator " + separator + " in the text. @patternTemplate may be incorrect.");
                return text;
            }

            String key = template.substring(0, splitIndexTemplate);
            if (target.equals(key)) {
                // match found
                return textCopy.substring(0, splitIndexText);
            }

            // not a match yet, update template and textCopy
            template = template.substring(splitIndexTemplate + 1);
            textCopy = textCopy.substring(splitIndexText + 1);
        }

        // return the original text if no match found
        return target.equals(template) ? textCopy : text;
    }

    private boolean checkConditions(DocStruct docstruct, MarcMetadataField configuredField, MetadataType conditionType) {
        // filter out a list of Metadata whose elements are all of conditionType
        List<? extends Metadata> conditionList = null;
        if (configuredField.isAnchorMetadata()) {
            if (docstruct.getParent() != null) {
                conditionList = docstruct.getParent().getAllMetadataByType(conditionType);
            } else {
                return false;
            }
        } else {
            conditionList = docstruct.getAllMetadataByType(conditionType);
        }

        if (conditionList == null || conditionList.isEmpty()) {
            // nothing found
            return false;
        }

        // look for a match
        boolean match = false;
        for (Metadata md : conditionList) {
            match = match || isMetadataAMatch(md, configuredField);
        }
        return match;
    }

    private boolean isMetadataAMatch(Metadata md, MarcMetadataField configuredField) {
        switch (configuredField.getConditionType()) {
            case "is":
                return md.getValue().equals(configuredField.getConditionValue());
            case "not":
                return !md.getValue().equals(configuredField.getConditionValue());
            case "any":
                return true;
            default:
                return false;
        }
    }

    private int getSortingTitleNumber(String value) {
        if (value.startsWith("A ")) {
            return 2;
        } else if (value.startsWith("An ")) {
            return 3;
        } else if (value.startsWith("Das ")) {
            return 4;
        } else if (value.startsWith("De ")) {
            return 3;
        } else if (value.startsWith("Dem ")) {
            return 4;
        } else if (value.startsWith("Den ")) {
            return 4;
        } else if (value.startsWith("Der ")) {
            return 4;
        } else if (value.startsWith("Des ")) {
            return 4;
        } else if (value.startsWith("Die ")) {
            return 4;
        } else if (value.startsWith("Een ")) {
            return 4;
        } else if (value.startsWith("Ein ")) {
            return 4;
        } else if (value.startsWith("Eine ")) {
            return 5;
        } else if (value.startsWith("Einem ")) {
            return 6;
        } else if (value.startsWith("Einen ")) {
            return 6;
        } else if (value.startsWith("Einer ")) {
            return 6;
        } else if (value.startsWith("Eines ")) {
            return 6;
        } else if (value.startsWith("El ")) {
            return 3;
        } else if (value.startsWith("En ")) {
            return 3;
        } else if (value.startsWith("Et ")) {
            return 3;
        } else if (value.startsWith("Gli ")) {
            return 4;
        } else if (value.startsWith("Het ")) {
            return 4;
        } else if (value.startsWith("I ")) {
            return 2;
        } else if (value.startsWith("Il ")) {
            return 3;
        } else if (value.startsWith("L' ")) {
            return 3;
        } else if (value.startsWith("La ")) {
            return 3;
        } else if (value.startsWith("Las ")) {
            return 4;
        } else if (value.startsWith("Le ")) {
            return 3;
        } else if (value.startsWith("Les ")) {
            return 4;
        } else if (value.startsWith("Lo ")) {
            return 3;
        } else if (value.startsWith("Los ")) {
            return 4;
        } else if (value.startsWith("The ")) {
            return 4;
        } else if (value.startsWith("Un ")) {
            return 3;
        } else if (value.startsWith("Un' ")) {
            return 4;
        } else if (value.startsWith("Una ")) {
            return 4;
        } else if (value.startsWith("Unas ")) {
            return 5;
        } else if (value.startsWith("Une ")) {
            return 4;
        } else if (value.startsWith("Uno ")) {
            return 4;
        } else if (value.startsWith("Unos ")) {
            return 5;
        }

        return 0;
    }

    private StringBuilder createLeader(MarcDocstructField docstruct) {
        StringBuilder leader = new StringBuilder();
        leader.append("xxxxx"); // 00-04 - Record length, empty
        leader.append("n"); // 05 - Record status, n=new
        if (StringUtils.isNotBlank(docstruct.getLeader6())) {
            leader.append(docstruct.getLeader6());
        } else {
            leader.append("a"); // 06 - Type of record, a - Language material
        }
        // 07 - Bibliographic level
        if (StringUtils.isNotBlank(docstruct.getLeader7())) {
            leader.append(docstruct.getLeader7());
        } else {
            leader.append("m"); // Monograph/Item
        }
        leader.append(" "); // 08 - Type of control - empty
        leader.append("a"); //09 - Character coding scheme
        leader.append("2"); //10 - Indicator count

        leader.append("2"); // 11 - Subfield code count
        leader.append("yyyyy"); // 12-16 - Base address of data
        leader.append("u");// 17 - Encoding level u - Unknown
        leader.append("u"); // 18 - Descriptive cataloging form u - Unknown
        // 19 - Multipart resource record level
        if (StringUtils.isNotBlank(docstruct.getLeader19())) {
            leader.append(docstruct.getLeader19());
        } else {
            leader.append(" ");
        }
        // 20 - Length of the length-of-field portion
        // 21 - Length of the starting-character-position portion
        // 22 - Length of the implementation-defined portion
        // 23 - Undefined
        leader.append("4500");
        return leader;
    }

    private Element createMainElement(Element rootElement, MarcMetadataField configuredField) {
        Element element = new Element(configuredField.getFieldType(), marc);
        element.setAttribute("tag", configuredField.getMarcMainTag());
        if ("datafield".equals(configuredField.getFieldType())) {
            element.setAttribute("ind1", configuredField.getInd1());
            element.setAttribute("ind2", configuredField.getInd2());
        }
        rootElement.addContent(element);
        return element;
    }

    private Fileformat getFileformat() {
        try {
            return step.getProzess().readMetadataFile();
        } catch (ReadException | IOException | SwapException e) {
            log.error(e);
        }
        return null;
    }
}
