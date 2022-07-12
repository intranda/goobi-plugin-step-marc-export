package de.intranda.goobi.plugins;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

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

import java.util.HashMap;
import java.util.List;

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
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.Corporate;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Person;
import ugh.dl.Prefs;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.exceptions.WriteException;

@PluginImplementation
@Log4j2
public class MarcexportStepPlugin implements IStepPluginVersion2 {

    @Getter
    private String title = "intranda_step_marcexport";
    @Getter
    private Step step;
    @Getter
    private String returnPath;

    private static final Namespace marc = Namespace.getNamespace("marc", "http://www.loc.gov/MARC21/slim");

    private List<MarcMetadataField> marcFields = new ArrayList<>();
    private List<MarcDocstructField> docstructFields = new ArrayList<>();

    @Override
    public void initialize(Step step, String returnPath) {
        this.returnPath = returnPath;
        this.step = step;

        SubnodeConfiguration myconfig = ConfigPlugins.getProjectAndStepConfig(title, step);
        myconfig.setExpressionEngine(new XPathExpressionEngine());

        List<HierarchicalConfiguration> hcl = myconfig.configurationsAt("/marcField");
        for (HierarchicalConfiguration hc : hcl) {
            String xpath = hc.getString("@xpath");
            String type = hc.getString("@type", "datafield");
            String mainTag = hc.getString("@mainTag");
            String ind1 = hc.getString("@ind1").replace("_", " ");
            String ind2 = hc.getString("@ind2").replace("_", " ");
            String subTag = hc.getString("@subTag");
            String repetitionMode = hc.getString("@reuseMode", "none");
            String rulesetName = hc.getString("@rulesetName");
            String additionalSubFieldCode = hc.getString("@additionalSubFieldCode");
            String additionalSubFieldValue = hc.getString("@additionalSubFieldValue");
            MarcMetadataField mmf = new MarcMetadataField(xpath, type, mainTag, ind1, ind2, subTag, repetitionMode, rulesetName,
                    additionalSubFieldCode, additionalSubFieldValue, hc.getBoolean("@anchorMetadata", false), hc.getString("@conditionField", null),
                    hc.getString("@conditionValue", null), hc.getString("@conditionType", "is"), hc.getString("@text", ""));
            marcFields.add(mmf);
        }

        hcl = myconfig.configurationsAt("/doctype");
        for (HierarchicalConfiguration hc : hcl) {
            boolean exportDocstruct = hc.getBoolean("@export");
            String docstructName = hc.getString("@rulesetName");
            String leader6 = hc.getString("@leader6");
            String leader7 = hc.getString("@leader7");
            String leader19 = hc.getString("@leader19");
            docstructFields.add(new MarcDocstructField(exportDocstruct, docstructName, leader6, leader7, leader19));
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
        boolean successful = true;
        // your logic goes here
        List<DocStruct> docstructList = new ArrayList<>();

        Prefs prefs = step.getProzess().getRegelsatz().getPreferences();
        Fileformat ff = getFileformat();
        if (ff == null) {
            // log error message
            return PluginReturnValue.ERROR;
        }
        try {
            DocStruct docstruct = ff.getDigitalDocument().getLogicalDocStruct();
            docstructList.add(docstruct);
            if (docstruct.getType().isAnchor()) {
                docstructList.add(docstruct.getAllChildren().get(0));
            }
        } catch (PreferencesException e1) {
            log.error(e1);
        }

        for (DocStruct docstruct : docstructList) {

            List additionalPersons = new ArrayList<>();
            List additionalCorporations = new ArrayList<>();

            MarcDocstructField currentField = null;

            for (MarcDocstructField field : docstructFields) {
                if (field.getDocstructName().equals(docstruct.getType().getName())) {
                    currentField = field;
                    break;
                }
            }

            // TODO check if anchor needs to be exported, check if it is master record

            if (currentField == null || !currentField.isExportDocstruct()) {
                continue;
            }

            StringBuilder leader = createLeader(currentField);

            Document marcDoc = new Document();
            Element recordElement = new Element("record", marc);
            marcDoc.setRootElement(recordElement);

            Element leaderElement = new Element("leader", marc);
            leaderElement.setText(leader.toString());
            recordElement.addContent(leaderElement);
            Element marcField = null;
            boolean firstAuthorWritten = false;

            for (MarcMetadataField configuredField : marcFields) {
                String type = configuredField.getRulesetName();
                MetadataType mdt = prefs.getMetadataTypeByName(type);
                MetadataType conditionType = null;
                if (StringUtils.isNotBlank(configuredField.getConditionField())) {
                    conditionType = prefs.getMetadataTypeByName(configuredField.getConditionField());
                }
                if (type == null) {
                    marcField = writeStaticMetadata(docstruct, recordElement, marcField, configuredField, conditionType);
                } else if (mdt.getIsPerson()) {
                    List<Person> list = docstruct.getAllPersonsByType(mdt);
                } else if (mdt.isCorporate()) {
                    List<Corporate> list = docstruct.getAllCorporatesByType(mdt);
                } else {
                    marcField = writeMetadata(docstruct, recordElement, marcField, configuredField, mdt, conditionType);
                }
            }

            XMLOutputter out = new XMLOutputter();
            out.setFormat(Format.getPrettyFormat());
            try {
                out.output(marcDoc, new FileOutputStream("/tmp/test.xml"));
            } catch (IOException e) {
                log.error(e);
            }
        }
        log.info("Marcexport step plugin executed");
        if (!successful) {
            return PluginReturnValue.ERROR;
        }
        return PluginReturnValue.FINISH;
    }

    private Element writeStaticMetadata(DocStruct docstruct, Element recordElement, Element marcField, MarcMetadataField configuredField,
            MetadataType conditionType) {
        if (StringUtils.isNotBlank(configuredField.getStaticText())) {
            // configured condition, check if they match
            if (conditionType != null) {
                boolean match = checkConditions(docstruct, configuredField, conditionType);
                if (!match) {
                    return marcField;
                }
            }
            marcField = generateMarcField(recordElement, marcField, configuredField);
            if ("controlfield".equals(configuredField.getFieldType())) {
                marcField.setText(configuredField.getStaticText());
            } else {
                Element subfield = new Element("subfield", marc);
                subfield.setAttribute("code", configuredField.getMarcSubTag());
                subfield.setText(configuredField.getStaticText());
                marcField.addContent(subfield);
            }
            if (StringUtils.isNotBlank(configuredField.getAdditionalSubFieldCode())) {
                Element subfield = new Element("subfield", marc);
                subfield.setAttribute("code", configuredField.getAdditionalSubFieldCode());
                subfield.setText(configuredField.getAdditionalSubFieldValue());
                marcField.addContent(subfield);
            }

        }

        return marcField;
    }

    private Element writeMetadata(DocStruct docstruct, Element recordElement, Element marcField, MarcMetadataField configuredField, MetadataType mdt,
            MetadataType conditionType) {

        List<? extends Metadata> list = null;
        if (configuredField.isAnchorMetadata()) {
            if (docstruct.getParent() != null) {
                list = docstruct.getParent().getAllMetadataByType(mdt);
            } else {
                return marcField;
            }
        } else {
            list = docstruct.getAllMetadataByType(mdt);
        }

        // configured condition, check if they match
        if (conditionType != null) {
            boolean match = checkConditions(docstruct, configuredField, conditionType);
            if (!match) {
                return marcField;
            }
        }

        if (list != null && !list.isEmpty()) {
            // always create a new element
            marcField = generateMarcField(recordElement, marcField, configuredField);
        }

        for (Metadata metadata : list) {
            if ("controlfield".equals(configuredField.getFieldType())) {
                marcField.setText(metadata.getValue());
            } else {
                Element subfield = new Element("subfield", marc);
                subfield.setAttribute("code", configuredField.getMarcSubTag());
                subfield.setText(metadata.getValue());
                marcField.addContent(subfield);

                if ("X".equals(marcField.getAttributeValue("ind2"))) {
                    // sorting title
                    int ind2Value = getSortingTitleNumber(metadata.getValue());
                    marcField.setAttribute("ind2", "" + ind2Value);
                }

            }
            if (StringUtils.isNotBlank(configuredField.getAdditionalSubFieldCode())) {
                Element subfield = new Element("subfield", marc);
                subfield.setAttribute("code", configuredField.getAdditionalSubFieldCode());
                subfield.setText(configuredField.getAdditionalSubFieldValue());
                marcField.addContent(subfield);
            }
        }
        return marcField;
    }

    private Element generateMarcField(Element recordElement, Element marcField, MarcMetadataField configuredField) {
        if ("none".equals(configuredField.getReuseMode())) {
            marcField = createMainElement(recordElement, configuredField);
        } else {
            if (marcField != null && marcField.getAttributeValue("tag").equals(configuredField.getMarcMainTag())
                    && configuredField.getInd1().equals(marcField.getAttributeValue("ind1"))
                    && ("X".equals(configuredField.getInd2()) || configuredField.getInd2().equals(marcField.getAttributeValue("ind2")))) {
                // re-use field
            } else {
                marcField = createMainElement(recordElement, configuredField);
            }
        }
        return marcField;
    }

    private boolean checkConditions(DocStruct docstruct, MarcMetadataField configuredField, MetadataType conditionType) {
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
            return false;
        }
        boolean match = false;
        for (Metadata md : conditionList) {
            switch (configuredField.getConditionType()) {
                case "is":
                    if (md.getValue().equals(configuredField.getConditionValue())) {
                        match = true;
                    }
                    break;
                case "not":
                    if (!md.getValue().equals(configuredField.getConditionValue())) {
                        match = true;
                    }
                    break;
                case "any":
                    match = true;
                    break;

                default:
                    break;
            }
        }
        return match;
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
        } catch (ReadException | PreferencesException | WriteException | IOException | InterruptedException | SwapException | DAOException e) {
            log.error(e);
        }
        return null;

    }
}
