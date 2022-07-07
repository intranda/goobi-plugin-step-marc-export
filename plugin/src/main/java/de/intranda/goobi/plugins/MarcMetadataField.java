package de.intranda.goobi.plugins;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

@Data
@AllArgsConstructor
public class MarcMetadataField {

    @NonNull
    // describes the xpath to the
    private String xpath;

    private String fieldType; // controlfield, datafield (default)

    @NonNull
    private String marcMainTag;

    private String ind1;

    private String ind2;


    private String marcSubTag;

    private String reuseMode; // subFied = create new subField within existing main field, none (default): always create a new main field

    private String rulesetName;


    private String additionalSubFieldCode;
    private String additionalSubFieldValue;
}
