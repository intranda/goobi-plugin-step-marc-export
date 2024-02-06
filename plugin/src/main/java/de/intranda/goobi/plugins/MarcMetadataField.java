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

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

@Data
@AllArgsConstructor
public class MarcMetadataField {

    private String fieldType; // controlfield, datafield (default)

    @NonNull
    private String marcMainTag;

    private String ind1;

    private String ind2;

    private String marcSubTag;

    // subFied = create new subField within existing main field
    // none (default): always create a new main field
    // group = use the same main field for a metadata group, but create a new one for the next group
    private String reuseMode;

    private String rulesetName;

    private String additionalSubFieldCode;
    private String additionalSubFieldValue;

    private boolean anchorMetadata = false;

    private String conditionField;
    private String conditionValue;
    private String conditionType; // is, not, any, matches

    private String staticText;

    private String wrapperLeft;
    private String wrapperRight;

    private String patternTemplate;
    private String patternTarget;

    private String mergeSeparator;

    private String regularExpression; // regular expression to manipulate the value. Usage is '/searchvalue/replacement/'

    private Map<String, String> replacements;

}
