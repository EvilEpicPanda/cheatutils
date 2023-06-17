package com.zergatul.cheatutils.configs;

import com.zergatul.cheatutils.utils.MathUtils;

public class SchematicaConfig extends BlockPlacerConfig implements ValidatableConfig {

    public boolean showMissingBlockGhosts;
    public double missingBlockGhostsMaxDistance;
    public boolean showMissingBlockTracers;
    public double missingBlockTracersMaxDistance;
    public boolean showMissingBlockCubes;
    public double missingBlockCubesMaxDistance;
    public boolean showWrongBlockTracers;
    public double wrongBlockTracersMaxDistance;
    public boolean showWrongBlockCubes;
    public double wrongBlockCubesMaxDistance;
    public boolean replaceableAsAir;
    public boolean airAlwaysValid;
    public boolean autoBuild;

    public SchematicaConfig() {
        super();

        showMissingBlockGhosts = true;
        missingBlockGhostsMaxDistance = 10;

        showMissingBlockTracers = false;
        missingBlockTracersMaxDistance = 30;

        showMissingBlockCubes = true;
        missingBlockCubesMaxDistance = 100;

        showWrongBlockTracers = false;
        wrongBlockTracersMaxDistance = 10;

        showWrongBlockCubes = false;
        wrongBlockCubesMaxDistance = 10;
    }

    @Override
    public void validate() {
        missingBlockGhostsMaxDistance = MathUtils.clamp(missingBlockGhostsMaxDistance, 1, 1000);
        missingBlockTracersMaxDistance = MathUtils.clamp(missingBlockTracersMaxDistance, 1, 1000);
        missingBlockCubesMaxDistance = MathUtils.clamp(missingBlockCubesMaxDistance, 1, 1000);
        wrongBlockTracersMaxDistance = MathUtils.clamp(wrongBlockTracersMaxDistance, 1, 1000);
        wrongBlockCubesMaxDistance = MathUtils.clamp(wrongBlockCubesMaxDistance, 1, 1000);
        super.validate();
    }
}