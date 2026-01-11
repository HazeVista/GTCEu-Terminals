package com.gtceuterminal.common.multiblock;

public enum ComponentType {
    INPUT_HATCH("Input Hatch"),
    OUTPUT_HATCH("Output Hatch"),
    INPUT_BUS("Input Bus"),
    OUTPUT_BUS("Output Bus"),
    ENERGY_HATCH("Energy Hatch"),
    PARALLEL_HATCH("Parallel Hatch"),
    MUFFLER("Muffler Hatch"),
    MAINTENANCE("Maintenance Hatch"),
    DUAL_HATCH("Dual Hatch"), //It doesnt work, next update maybe
    CASING("Casing"), //It doesnt work, next update maybe
    COIL("Heating Coil"),
    PIPE("Pipe"), //It doesnt work, next update maybe
    OTHER("Other");

    private final String displayName;

    ComponentType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isUpgradeable() {
        return switch (this) {
            case INPUT_HATCH, OUTPUT_HATCH, INPUT_BUS, OUTPUT_BUS,
                 ENERGY_HATCH, PARALLEL_HATCH, MUFFLER, MAINTENANCE, DUAL_HATCH, COIL -> true;
            default -> false;
        };
    }

    public String getIcon() {
        return switch (this) {
            case INPUT_HATCH, INPUT_BUS -> "⬇";
            case OUTPUT_HATCH, OUTPUT_BUS -> "⬆";
            case PARALLEL_HATCH -> "⇉";
            case OTHER -> "•";
            default -> "Unknown";
        };
    }
}