package it.tdevelopments.ffapure.gui;

public final class ItemPickerGui extends BaseGuiHolder {

    public static final String ID = "GUI_ITEM_PICKER";
    private final int layoutSlot;
    private final String targetSlotKey;
    private final String categoryId;

    public ItemPickerGui(String modeId, int layoutSlot, String targetSlotKey, String categoryId) {
        super(ID, modeId);
        this.layoutSlot = layoutSlot;
        this.targetSlotKey = targetSlotKey;
        this.categoryId = categoryId;
    }

    public int getLayoutSlot() {
        return layoutSlot;
    }

    public String getTargetSlotKey() {
        return targetSlotKey;
    }

    public String getCategoryId() {
        return categoryId;
    }
}
