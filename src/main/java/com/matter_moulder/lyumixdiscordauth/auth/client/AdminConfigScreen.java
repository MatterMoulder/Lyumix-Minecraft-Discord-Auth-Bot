package com.matter_moulder.lyumixdiscordauth.auth.client;

import com.matter_moulder.lyumixdiscordauth.auth.ModPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

public class AdminConfigScreen extends Screen {
    private static final String[] FLOW_MODES = new String[]{"legacy", "oauth_notify_fallback", "oauth_only", "notify_only"};
    private static final int LABEL_COLOR = 0xD8D8D8;
    private static final int HINT_COLOR = 0x9AA0A6;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_MIN_THUMB = 20;

    private final List<UiLabel> labels = new ArrayList<>();
    private final List<ScrollEntry> scrollEntries = new ArrayList<>();

    private int scrollOffset;
    private int maxScroll;
    private boolean draggingScrollbar;
    private int scrollbarDragOffset;

    private boolean useDiscordOAuth;
    private boolean roleCheckEnabled;
    private boolean requireGuildMembership;
    private boolean allowUserUnlink;
    private boolean requireAllRoles;
    private boolean blindnessWhileLogin;
    private boolean timerEnabled;
    private int flowModeIndex;

    private String discordServerId;
    private String oauthCallbackServerPort;
    private String discordOAuthRedirectUri;
    private String requiredRoleIdsCsv;
    private String botToken;
    private String discordClientId;
    private String discordClientSecret;
    private String autoLoginTime;
    private String timerLoginTime;
    private String timerTitle;
    private String firstColor;
    private String secondColor;
    private String thirdColor;
    private String secondTime;
    private String thirdTime;

    private ButtonWidget oauthButton;
    private ButtonWidget roleButton;
    private ButtonWidget requireGuildMembershipButton;
    private ButtonWidget allowUnlinkButton;
    private ButtonWidget requireAllRolesButton;
    private ButtonWidget blindnessButton;
    private ButtonWidget timerEnabledButton;
    private ButtonWidget flowButton;

    private TextFieldWidget discordServerField;
    private TextFieldWidget oauthPortField;
    private TextFieldWidget redirectUriField;
    private TextFieldWidget requiredRoleIdsField;
    private TextFieldWidget botTokenField;
    private TextFieldWidget clientIdField;
    private TextFieldWidget clientSecretField;
    private TextFieldWidget autoLoginTimeField;
    private TextFieldWidget timerLoginTimeField;
    private TextFieldWidget timerTitleField;
    private TextFieldWidget firstColorField;
    private TextFieldWidget secondColorField;
    private TextFieldWidget thirdColorField;
    private TextFieldWidget secondTimeField;
    private TextFieldWidget thirdTimeField;

    private record UiLabel(int x, int baseY, Text text, int color) {
    }

    private record ScrollEntry(ClickableWidget widget, int baseY) {
    }

    public AdminConfigScreen(NbtCompound data) {
        super(Text.literal("LDA Admin Settings"));
        this.useDiscordOAuth = data.getBoolean("discord.useDiscordOAuth");
        this.roleCheckEnabled = data.getBoolean("discord.roleCheckEnabled");
        this.requireGuildMembership = data.getBoolean("discord.requireGuildMembership");
        this.allowUserUnlink = data.getBoolean("discord.allowUserUnlink");
        this.requireAllRoles = data.getBoolean("discord.requireAllRoles");
        this.blindnessWhileLogin = data.getBoolean("login.blindnessWhileLogin");
        this.timerEnabled = data.getBoolean("timer.enabled");
        this.flowModeIndex = indexOfFlow(data.getString("discord.authFlowMode"));

        this.discordServerId = data.getString("discord.discordServerId");
        this.oauthCallbackServerPort = String.valueOf(data.getInt("discord.oauthCallbackServerPort"));
        this.discordOAuthRedirectUri = data.getString("discord.discordOAuthRedirectUri");
        this.requiredRoleIdsCsv = data.getString("discord.requiredRoleIdsCsv");
        this.botToken = data.getString("discord.botTokenMasked");
        this.discordClientId = data.getString("discord.clientIdMasked");
        this.discordClientSecret = data.getString("discord.clientSecretMasked");

        this.autoLoginTime = String.valueOf(data.getInt("login.autoLoginTime"));
        this.timerLoginTime = String.valueOf(data.getInt("timer.loginTime"));
        this.timerTitle = data.getString("timer.title");
        this.firstColor = data.getString("timer.firstColor");
        this.secondColor = data.getString("timer.secondColor");
        this.thirdColor = data.getString("timer.thirdColor");
        this.secondTime = String.valueOf(data.getInt("timer.secondTime"));
        this.thirdTime = String.valueOf(data.getInt("timer.thirdTime"));
    }

    @Override
    protected void init() {
        labels.clear();
        scrollEntries.clear();

        int cx = this.width / 2;
        int y = contentTop();

        addLabel(cx - 100, y, "Auth / Flow", LABEL_COLOR);
        y += 12;

        oauthButton = addScrolledButton(ButtonWidget.builder(Text.empty(), button -> {
            useDiscordOAuth = !useDiscordOAuth;
            refreshTexts();
        }).dimensions(cx - 100, y, 200, 20).build(), y);
        y += 24;

        flowButton = addScrolledButton(ButtonWidget.builder(Text.empty(), button -> {
            flowModeIndex = (flowModeIndex + 1) % FLOW_MODES.length;
            refreshTexts();
        }).dimensions(cx - 100, y, 200, 20).build(), y);
        y += 24;

        roleButton = addScrolledButton(ButtonWidget.builder(Text.empty(), button -> {
            roleCheckEnabled = !roleCheckEnabled;
            refreshTexts();
        }).dimensions(cx - 100, y, 200, 20).build(), y);
        y += 24;

        requireGuildMembershipButton = addScrolledButton(ButtonWidget.builder(Text.empty(), button -> {
            requireGuildMembership = !requireGuildMembership;
            refreshTexts();
        }).dimensions(cx - 100, y, 200, 20).build(), y);
        y += 24;

        allowUnlinkButton = addScrolledButton(ButtonWidget.builder(Text.empty(), button -> {
            allowUserUnlink = !allowUserUnlink;
            refreshTexts();
        }).dimensions(cx - 100, y, 200, 20).build(), y);
        y += 24;

        requireAllRolesButton = addScrolledButton(ButtonWidget.builder(Text.empty(), button -> {
            requireAllRoles = !requireAllRoles;
            refreshTexts();
        }).dimensions(cx - 100, y, 200, 20).build(), y);
        y += 34;

        addLabel(cx - 100, y, "Discord / OAuth", LABEL_COLOR);
        y += 24;

        discordServerField = addField(cx, y, "Discord Guild ID", discordServerId, 64);
        y += 34;
        oauthPortField = addField(cx, y, "OAuth callback port", oauthCallbackServerPort, 8);
        y += 34;
        redirectUriField = addField(cx, y, "OAuth redirect URI", discordOAuthRedirectUri, 256);
        y += 34;
        requiredRoleIdsField = addField(cx, y, "Required role IDs (comma-separated)", requiredRoleIdsCsv, 512);
        y += 30;
        addLabel(cx - 100, y, "Leave Guild ID empty to allow any guild", HINT_COLOR);
        y += 20;

        addLabel(cx - 100, y, "Discord secrets", LABEL_COLOR);
        y += 24;

        botTokenField = addField(cx, y, "Discord bot token", botToken, 512);
        y += 34;
        clientIdField = addField(cx, y, "Discord OAuth client ID", discordClientId, 128);
        y += 34;
        clientSecretField = addField(cx, y, "Discord OAuth client secret", discordClientSecret, 512);
        y += 30;
        addLabel(cx - 100, y, "Tip: masked/empty secret fields keep current value", HINT_COLOR);
        y += 20;

        addLabel(cx - 100, y, "Login / Timer", LABEL_COLOR);
        y += 18;

        blindnessButton = addScrolledButton(ButtonWidget.builder(Text.empty(), button -> {
            blindnessWhileLogin = !blindnessWhileLogin;
            refreshTexts();
        }).dimensions(cx - 100, y, 200, 20).build(), y);
        y += 24;

        timerEnabledButton = addScrolledButton(ButtonWidget.builder(Text.empty(), button -> {
            timerEnabled = !timerEnabled;
            refreshTexts();
        }).dimensions(cx - 100, y, 200, 20).build(), y);
        y += 34;

        autoLoginTimeField = addField(cx, y, "Auto login time (hours)", autoLoginTime, 8);
        y += 34;
        timerLoginTimeField = addField(cx, y, "Auth timeout (seconds)", timerLoginTime, 8);
        y += 34;
        timerTitleField = addField(cx, y, "Action bar timer title", timerTitle, 128);
        y += 34;
        firstColorField = addField(cx, y, "Timer color #1", firstColor, 32);
        y += 34;
        secondColorField = addField(cx, y, "Timer color #2", secondColor, 32);
        y += 34;
        thirdColorField = addField(cx, y, "Timer color #3", thirdColor, 32);
        y += 34;
        secondTimeField = addField(cx, y, "Switch to color #2 at (sec)", secondTime, 8);
        y += 34;
        thirdTimeField = addField(cx, y, "Switch to color #3 at (sec)", thirdTime, 8);
        y += 30;
        addLabel(cx - 100, y, "Color names: GREEN, YELLOW, RED, ...", HINT_COLOR);
        y += 20;

        maxScroll = Math.max(0, y - viewportBottom());
        setScrollOffset(scrollOffset);
        refreshTexts();

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> saveAndClose())
                .dimensions(cx - 100, this.height - 28, 96, 20)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), button -> close())
                .dimensions(cx + 4, this.height - 28, 96, 20)
                .build());
    }

    private ButtonWidget addScrolledButton(ButtonWidget button, int baseY) {
        scrollEntries.add(new ScrollEntry(button, baseY));
        return this.addDrawableChild(button);
    }

    private TextFieldWidget addField(int centerX, int y, String label, String value, int maxLength) {
        addLabel(centerX - 100, y - 11, label, LABEL_COLOR);
        TextFieldWidget field = new TextFieldWidget(this.textRenderer, centerX - 100, y, 200, 20, Text.literal(label));
        field.setMaxLength(maxLength);
        field.setText(value == null ? "" : value);
        scrollEntries.add(new ScrollEntry(field, y));
        return this.addDrawableChild(field);
    }

    private void addLabel(int x, int y, String text, int color) {
        labels.add(new UiLabel(x, y, Text.literal(text), color));
    }

    private void applyScroll() {
        int top = contentTop();
        int bottom = viewportBottom();
        for (ScrollEntry entry : scrollEntries) {
            ClickableWidget widget = entry.widget();
            int y = entry.baseY() - scrollOffset;
            widget.setY(y);
            boolean visible = y + widget.getHeight() >= top && y <= bottom;
            widget.visible = visible;
            widget.active = visible;
        }
    }

    private void setScrollOffset(int value) {
        scrollOffset = MathHelper.clamp(value, 0, maxScroll);
        applyScroll();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (maxScroll <= 0) {
            return super.mouseScrolled(mouseX, mouseY, amount);
        }
        setScrollOffset(scrollOffset - (int) (amount * 20));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && maxScroll > 0 && isMouseOverScrollbar(mouseX, mouseY)) {
            int thumbTop = scrollbarThumbTop();
            int thumbBottom = thumbTop + scrollbarThumbHeight();
            if (mouseY >= thumbTop && mouseY <= thumbBottom) {
                draggingScrollbar = true;
                scrollbarDragOffset = (int) mouseY - thumbTop;
            } else {
                draggingScrollbar = true;
                scrollbarDragOffset = scrollbarThumbHeight() / 2;
                setScrollOffset(scrollFromThumbTop((int) mouseY - scrollbarDragOffset));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingScrollbar && button == 0 && maxScroll > 0) {
            setScrollOffset(scrollFromThumbTop((int) mouseY - scrollbarDragOffset));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            draggingScrollbar = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private int contentTop() {
        return 40;
    }

    private int viewportBottom() {
        return this.height - 64;
    }

    private int scrollbarX() {
        return this.width / 2 + 112;
    }

    private int scrollbarY() {
        return contentTop();
    }

    private int scrollbarHeight() {
        return viewportBottom() - contentTop();
    }

    private int scrollbarThumbHeight() {
        if (maxScroll <= 0) {
            return scrollbarHeight();
        }
        int contentHeight = scrollbarHeight() + maxScroll;
        int thumb = (int) ((long) scrollbarHeight() * scrollbarHeight() / Math.max(1, contentHeight));
        return MathHelper.clamp(thumb, SCROLLBAR_MIN_THUMB, scrollbarHeight());
    }

    private int scrollbarThumbTop() {
        if (maxScroll <= 0) {
            return scrollbarY();
        }
        int travel = Math.max(1, scrollbarHeight() - scrollbarThumbHeight());
        float progress = scrollOffset / (float) maxScroll;
        return scrollbarY() + Math.round(progress * travel);
    }

    private int scrollFromThumbTop(int thumbTop) {
        int travel = Math.max(1, scrollbarHeight() - scrollbarThumbHeight());
        int clamped = MathHelper.clamp(thumbTop, scrollbarY(), scrollbarY() + travel);
        float progress = (clamped - scrollbarY()) / (float) travel;
        return Math.round(progress * maxScroll);
    }

    private boolean isMouseOverScrollbar(double mouseX, double mouseY) {
        return mouseX >= scrollbarX()
                && mouseX <= scrollbarX() + SCROLLBAR_WIDTH
                && mouseY >= scrollbarY()
                && mouseY <= scrollbarY() + scrollbarHeight();
    }

    private void captureInputs() {
        if (discordServerField != null) {
            discordServerId = discordServerField.getText();
        }
        if (oauthPortField != null) {
            oauthCallbackServerPort = oauthPortField.getText();
        }
        if (redirectUriField != null) {
            discordOAuthRedirectUri = redirectUriField.getText();
        }
        if (requiredRoleIdsField != null) {
            requiredRoleIdsCsv = requiredRoleIdsField.getText();
        }
        if (botTokenField != null) {
            botToken = botTokenField.getText();
        }
        if (clientIdField != null) {
            discordClientId = clientIdField.getText();
        }
        if (clientSecretField != null) {
            discordClientSecret = clientSecretField.getText();
        }

        if (autoLoginTimeField != null) {
            autoLoginTime = autoLoginTimeField.getText();
        }
        if (timerLoginTimeField != null) {
            timerLoginTime = timerLoginTimeField.getText();
        }
        if (timerTitleField != null) {
            timerTitle = timerTitleField.getText();
        }
        if (firstColorField != null) {
            firstColor = firstColorField.getText();
        }
        if (secondColorField != null) {
            secondColor = secondColorField.getText();
        }
        if (thirdColorField != null) {
            thirdColor = thirdColorField.getText();
        }
        if (secondTimeField != null) {
            secondTime = secondTimeField.getText();
        }
        if (thirdTimeField != null) {
            thirdTime = thirdTimeField.getText();
        }

    }

    private void refreshTexts() {
        if (oauthButton != null) {
            oauthButton.setMessage(Text.literal("OAuth enabled: " + yesNo(useDiscordOAuth)));
        }
        if (flowButton != null) {
            flowButton.setMessage(Text.literal("Flow mode: " + readableFlow(FLOW_MODES[flowModeIndex])));
        }
        if (roleButton != null) {
            roleButton.setMessage(Text.literal("Role check: " + yesNo(roleCheckEnabled)));
        }
        if (allowUnlinkButton != null) {
            allowUnlinkButton.setMessage(Text.literal("Allow /unlink command: " + yesNo(allowUserUnlink)));
        }
        if (requireGuildMembershipButton != null) {
            requireGuildMembershipButton.setMessage(Text.literal("Require guild membership: " + yesNo(requireGuildMembership)));
        }
        if (requireAllRolesButton != null) {
            requireAllRolesButton.setMessage(Text.literal("Require all roles: " + yesNo(requireAllRoles)));
        }
        if (blindnessButton != null) {
            blindnessButton.setMessage(Text.literal("Blindness while auth: " + yesNo(blindnessWhileLogin)));
        }
        if (timerEnabledButton != null) {
            timerEnabledButton.setMessage(Text.literal("Timeout timer enabled: " + yesNo(timerEnabled)));
        }
    }

    private static String yesNo(boolean value) {
        return value ? "ON" : "OFF";
    }

    private static String readableFlow(String flow) {
        return switch (flow) {
            case "oauth_notify_fallback" -> "OAuth -> Notify fallback";
            case "oauth_only" -> "OAuth only";
            case "notify_only" -> "Notify only";
            case "legacy" -> "Legacy (use old toggle)";
            default -> flow;
        };
    }

    private void saveAndClose() {
        captureInputs();
        NbtCompound data = new NbtCompound();
        data.putBoolean("discord.useDiscordOAuth", useDiscordOAuth);
        data.putString("discord.authFlowMode", FLOW_MODES[flowModeIndex]);
        data.putBoolean("discord.roleCheckEnabled", roleCheckEnabled);
        data.putBoolean("discord.requireGuildMembership", requireGuildMembership);
        data.putBoolean("discord.allowUserUnlink", allowUserUnlink);
        data.putBoolean("discord.requireAllRoles", requireAllRoles);
        data.putString("discord.discordServerId", discordServerId == null ? "" : discordServerId);
        data.putString("discord.oauthCallbackServerPort", oauthCallbackServerPort == null ? "" : oauthCallbackServerPort);
        data.putString("discord.discordOAuthRedirectUri", discordOAuthRedirectUri == null ? "" : discordOAuthRedirectUri);
        data.putString("discord.requiredRoleIdsCsv", requiredRoleIdsCsv == null ? "" : requiredRoleIdsCsv);
        data.putString("discord.botToken", botToken == null ? "" : botToken);
        data.putString("discord.clientId", discordClientId == null ? "" : discordClientId);
        data.putString("discord.clientSecret", discordClientSecret == null ? "" : discordClientSecret);

        data.putBoolean("login.blindnessWhileLogin", blindnessWhileLogin);
        data.putString("login.autoLoginTime", autoLoginTime == null ? "" : autoLoginTime);

        data.putBoolean("timer.enabled", timerEnabled);
        data.putString("timer.loginTime", timerLoginTime == null ? "" : timerLoginTime);
        data.putString("timer.title", timerTitle == null ? "" : timerTitle);
        data.putString("timer.firstColor", firstColor == null ? "" : firstColor);
        data.putString("timer.secondColor", secondColor == null ? "" : secondColor);
        data.putString("timer.thirdColor", thirdColor == null ? "" : thirdColor);
        data.putString("timer.secondTime", secondTime == null ? "" : secondTime);
        data.putString("timer.thirdTime", thirdTime == null ? "" : thirdTime);


        var packet = PacketByteBufs.create();
        packet.writeNbt(data);
        ClientPlayNetworking.send(ModPackets.ADMIN_CONFIG_SAVE_C2S_ID, packet);
        this.close();
    }

    private static int indexOfFlow(String mode) {
        if (mode == null) {
            return 0;
        }
        for (int i = 0; i < FLOW_MODES.length; i++) {
            if (FLOW_MODES[i].equalsIgnoreCase(mode)) {
                return i;
            }
        }
        return 0;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Scroll with mouse wheel"), this.width / 2, 24, 0xCFE8FF);
        for (UiLabel label : labels) {
            int y = label.baseY() - scrollOffset;
            if (y >= contentTop() - 12 && y <= viewportBottom()) {
                int maxLabelWidth = Math.max(0, scrollbarX() - 6 - label.x());
                if (maxLabelWidth > 0) {
                    String clipped = this.textRenderer.trimToWidth(label.text().getString(), maxLabelWidth);
                    context.drawTextWithShadow(this.textRenderer, Text.literal(clipped), label.x(), y, label.color());
                }
            }
        }

        int barX = scrollbarX();
        int barY = scrollbarY();
        int barH = scrollbarHeight();
        context.fill(barX, barY, barX + SCROLLBAR_WIDTH, barY + barH, 0x66202020);
        int thumbY = scrollbarThumbTop();
        int thumbH = scrollbarThumbHeight();
        context.fill(barX, thumbY, barX + SCROLLBAR_WIDTH, thumbY + thumbH, 0xFF8A8A8A);
        context.fill(barX, thumbY, barX + SCROLLBAR_WIDTH, thumbY + 1, 0xFFE0E0E0);
        context.fill(barX, thumbY + thumbH - 1, barX + SCROLLBAR_WIDTH, thumbY + thumbH, 0xFF5A5A5A);

        context.fill(this.width / 2 - 104, this.height - 34, this.width / 2 + 104, this.height - 33, 0x88FFFFFF);
    }
}
