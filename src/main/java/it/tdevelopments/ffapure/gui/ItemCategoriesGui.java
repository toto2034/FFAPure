package it.tdevelopments.ffapure.gui;

public final class ItemCategoriesGui extends BaseGuiHolder {

    public static final String ID = "GUI_ITEM_CATEGORIES";
    private final int layoutSlot;
    private final String targetSlotKey;

    public ItemCategoriesGui(String modeId, int layoutSlot, String targetSlotKey) {
        super(ID, modeId);
        this.layoutSlot = layoutSlot;
        this.targetSlotKey = targetSlotKey;
    }

    public int getLayoutSlot() {
        return layoutSlot;
    }

    public String getTargetSlotKey() {
        return targetSlotKey;
    }
}
