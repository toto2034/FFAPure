package it.tdevelopments.ffapure.gui;

public final class KitBrowserGui extends BaseGuiHolder {

    public static final String ID = "GUI_KIT_BROWSER";
    public static final String NORMAL_KITS_PATH = ID + ".normal-kits";
    public static final String PREMIUM_KITS_PATH = ID + ".premium-kits";
    public static final String PREMADE_PATH = ID + ".premade-kit";
    public static final String PREMADE_DISABLED_PATH = ID + ".premade-kit-disabled";
    public static final String CLEAR_INVENTORY_PATH = ID + ".clear-inventory";
    public static final String LOCKED_ITEM_PATH = "locked-item";
    public static final String LOCKED_MESSAGE_KEY = "locked-message";
    public static final String PERMISSION_KEY = "permission";
    public static final String PERMISSIONS_KEY = "permissions";

    public KitBrowserGui(String modeId) {
        super(ID, modeId);
    }
}
