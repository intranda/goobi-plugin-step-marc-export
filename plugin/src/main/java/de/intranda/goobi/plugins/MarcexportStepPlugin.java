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
            String additionalSubFieldCode=hc.getString("@additionalSubFieldCode");
            String additionalSubFieldValue=hc.getString("@additionalSubFieldValue");
            MarcMetadataField mmf = new MarcMetadataField(xpath, type, mainTag, ind1, ind2, subTag, repetitionMode, rulesetName, additionalSubFieldCode, additionalSubFieldValue);
            marcFields.add(mmf);
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
        return null;
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

        DocStruct docstruct = null;
        Prefs prefs = step.getProzess().getRegelsatz().getPreferences();
        try {
            Fileformat ff = step.getProzess().readMetadataFile();
            docstruct = ff.getDigitalDocument().getLogicalDocStruct();

        } catch (ReadException | PreferencesException | WriteException | IOException | InterruptedException | SwapException | DAOException e) {
            log.error(e);
        }

        StringBuilder leader = new StringBuilder();
        leader.append("     "); // 00-04 - Record length, empty
        leader.append("n"); // 05 - Record status, n=new
        leader.append("a"); // 06 - Type of record, a - Language material
        // 07 - Bibliographic level
        if (docstruct.getType().isAnchor()) {
            leader.append("s"); // Serial
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
        if (docstruct.getType().isAnchor()) {
            leader.append("a"); // a - Set
        } else {
            leader.append(" ");
        }
        // 20 - Length of the length-of-field portion
        // 21 - Length of the starting-character-position portion
        // 22 - Length of the implementation-defined portion
        // 23 - Undefined
        leader.append("4500");

        Document marcDoc = new Document();
        Element record = new Element("record", marc);
        marcDoc.setRootElement(record);

        Element leaderElement = new Element("leader", marc);
        leaderElement.setText(leader.toString());
        Element marcField = null;
        for (MarcMetadataField configuredField : marcFields) {
            String type = configuredField.getRulesetName();
            MetadataType mdt = prefs.getMetadataTypeByName(type);
            if (mdt.isCorporate()) {
                List<Corporate> list = docstruct.getAllCorporatesByType(mdt);
            } else if (mdt.getIsPerson()) {
                List<Person> list = docstruct.getAllPersonsByType(mdt);
            } else {
                List<? extends Metadata> list = docstruct.getAllMetadataByType(mdt);
                if (list != null && !list.isEmpty()) {

                    // always create a new element
                    if ("none".equals(configuredField.getReuseMode())) {
                        marcField = createMainElement(record, configuredField);
                    } else {
                        if (marcField.getAttributeValue("tag").equals(configuredField.getMarcMainTag())
                                && configuredField.getInd1().equals(marcField.getAttributeValue("ind1"))
                                && ("X".equals(configuredField.getInd2()) || configuredField.getInd2().equals(marcField.getAttributeValue("ind2")))) {
                        } else {
                            marcField = createMainElement(record, configuredField);
                            if ("X".equals(configuredField.getInd2())) {
                                // sorting title
                            } else {

                            }
                        }
                    }
                }

                for (Metadata metadata : list) {
                    if ("controlfield".equals(configuredField.getFieldType())) {
                        marcField.setText(metadata.getValue());
                    } else {
                        Element subfield = new Element("subfield", marc);
                        subfield.setAttribute("code", configuredField.getMarcSubTag());
                        subfield.setText(metadata.getValue());
                        marcField.addContent(subfield);
                    }
                    if (StringUtils.isNotBlank(configuredField.getAdditionalSubFieldCode())) {
                        Element subfield = new Element("subfield", marc);
                        subfield.setAttribute("code", configuredField.getAdditionalSubFieldCode());
                        subfield.setText(configuredField.getAdditionalSubFieldValue());
                        marcField.addContent(subfield);
                    }
                }
            }
        }

        XMLOutputter out = new XMLOutputter();
        out.setFormat(Format.getPrettyFormat());
        try {
            out.output(marcDoc, new FileOutputStream("/tmp/test.xml"));
        } catch (IOException e) {
            log.error(e);
        }

        log.info("Marcexport step plugin executed");
        if (!successful) {
            return PluginReturnValue.ERROR;
        }
        return PluginReturnValue.FINISH;
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
}
