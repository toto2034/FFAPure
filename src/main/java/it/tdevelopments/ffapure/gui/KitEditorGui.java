package it.tdevelopments.ffapure.gui;

public final class KitEditorGui extends BaseGuiHolder {

    public static final String ID = "GUI_KIT_EDITOR";
    public static final String IMPORT_ACTION = "IMPORT_LAYOUT";
    private final int layoutSlot;

    public KitEditorGui(String modeId, int layoutSlot) {
        super(ID, modeId);
        this.layoutSlot = layoutSlot;
    }

    public int getLayoutSlot() {
        return layoutSlot;
    }
}
