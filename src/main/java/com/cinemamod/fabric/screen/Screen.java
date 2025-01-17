package com.cinemamod.fabric.screen;

import com.cinemamod.fabric.block.ScreenBlock;
import com.cinemamod.fabric.buffer.PacketByteBufSerializable;
import com.cinemamod.fabric.cef.CefUtil;
import com.cinemamod.fabric.video.Video;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;
import org.apache.commons.lang3.NotImplementedException;
import org.cef.browser.CefBrowserOsr;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Screen implements PacketByteBufSerializable<Screen> {

    private int x;
    private int y;
    private int z;
    private String facing;
    private float width;
    private float height;
    private boolean visible;
    private boolean muted;

    private transient List<PreviewScreen> previewScreens;
    private transient CefBrowserOsr browser;
    private transient Video video;
    private transient boolean unregistered;
    private transient BlockPos blockPos; // used as a cache for performance

    public Screen(int x, int y, int z, String facing, int width, int height, boolean visible, boolean muted) {
        this();
        this.x = x;
        this.y = y;
        this.z = z;
        this.facing = facing;
        this.width = width;
        this.height = height;
        this.visible = visible;
        this.muted = muted;
    }

    public Screen() {
        previewScreens = new ArrayList<>();
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public BlockPos getPos() {
        if (blockPos == null) {
            blockPos = new BlockPos(x, y, z);
        }

        return blockPos;
    }

    public String getFacing() {
        return facing;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean isMuted() {
        return muted;
    }

    public List<PreviewScreen> getPreviewScreens() {
        return previewScreens;
    }

    public void addPreviewScreen(PreviewScreen previewScreen) {
        previewScreens.add(previewScreen);
    }

    public CefBrowserOsr getBrowser() {
        return browser;
    }

    public boolean hasBrowser() {
        return browser != null;
    }

    public void reload() {
        if (video != null) {
            loadVideo(video);
        }
    }

    public void loadVideo(Video video) {
        this.video = video;
        closeBrowser();
        visible = false;

        String url = video.getVideoInfo().getVideoService().getUrl();
        if (url.contains("%s")) {
            url = String.format(url, video.getVideoInfo().getId());
        }

        browser = CefUtil.createBrowser(url, this);


        //browser = CefUtil.createBrowser("https://www.svtplay.se/kanaler/svt1?start=auto", this);
    }

    public void closeBrowser() {
        if (browser != null) {
            browser.close();
            browser = null;
        }
    }

    public Video getVideo() {
        return video;
    }

    public void setVideoVolume(float volume) {
        if (browser != null && video != null && browser.getMainFrame() != null) {
            String js = video.getVideoInfo().getVideoService().getSetVolumeJs();


            // 0-100 volume
            if (js.contains("%d")) {
                js = String.format(js, (int) (volume * 100));
            }

            // 0.00-1.00 volume
            else if (js.contains("%f")) {
                js = String.format(Locale.US, js, volume);
            }
            browser.getMainFrame().executeJavaScript(js, browser.getURL(), 0);
        }
    }


    public void startVideo() {
        //MinecraftClient.getInstance().player.sendChatMessage("Starting video", null);
        if (browser != null && video != null) {
            /*
            String fullScreenScript = "var player = document.getElementsByClassName(\"_video-player_qoxkq_1 _video-player--16-9_qoxkq_1\")[0];\n" +
                    "element = document.getElementsByClassName(\"_video-player_qoxkq_1 _video-player--16-9_qoxkq_1\")[0];\n" +
                    "element.parentNode.removeChild(element);\n" +
                    "document.body.appendChild(player);\n" +
                    "element = document.getElementById(\"__next\");\n" +
                    "element.parentNode.removeChild(element)";

            browser.executeJavaScript(fullScreenScript, browser.getURL(), 0);
            */
            String startJs = video.getVideoInfo().getVideoService().getStartJs();

            if (startJs.contains("%s") && startJs.contains("%b")) {
                startJs = String.format(startJs, video.getVideoInfo().getId(), video.getVideoInfo().isLivestream());
            } else if (startJs.contains("%s")) {
                startJs = String.format(startJs, video.getVideoInfo().getId());
            }

            browser.getMainFrame().executeJavaScript(startJs, browser.getURL(), 0);

            // Seek to current time
            if (!video.getVideoInfo().isLivestream()) {
                long millisSinceStart = System.currentTimeMillis() - video.getStartedAt();
                long secondsSinceStart = millisSinceStart / 1000;
                if (secondsSinceStart < video.getVideoInfo().getDurationSeconds()) {
                    String seekJs = video.getVideoInfo().getVideoService().getSeekJs();

                    if (seekJs.contains("%d")) {
                        seekJs = String.format(seekJs, secondsSinceStart);
                    }

                    browser.getMainFrame().executeJavaScript(seekJs, browser.getURL(), 0);
                }
            }
            visible = true;
        }
    }

    public void seekVideo(int seconds) {
        // TODO:
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public void register() {
        if (MinecraftClient.getInstance().world == null) {
            return;
        }

        int chunkX = x >> 4;
        int chunkZ = z >> 4;

        if (MinecraftClient.getInstance().world.isChunkLoaded(chunkX, chunkZ)) {
            MinecraftClient.getInstance().world.setBlockState(getBlockPos(), ScreenBlock.SCREEN_BLOCK.getDefaultState());
        }

        ClientChunkEvents.CHUNK_LOAD.register((clientWorld, worldChunk) -> {
            if (unregistered) {
                return;
            }

            // If the loaded chunk has this screen block in it, place it in the world
            if (worldChunk.getPos().x == chunkX && worldChunk.getPos().z == chunkZ) {
                clientWorld.setBlockState(getBlockPos(), ScreenBlock.SCREEN_BLOCK.getDefaultState());
            }
        });
    }

    public void unregister() {
        unregistered = true;

        if (MinecraftClient.getInstance().world != null) {
            MinecraftClient.getInstance().world.setBlockState(getBlockPos(), Blocks.AIR.getDefaultState());
        }
    }

    @Override
    public Screen fromBytes(PacketByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        facing = buf.readString();
        width = buf.readFloat();
        height = buf.readFloat();
        visible = buf.readBoolean();
        muted = buf.readBoolean();
        return this;
    }

    @Override
    public void toBytes(PacketByteBuf buf) {
        throw new NotImplementedException("Not implemented on client");
    }

}
