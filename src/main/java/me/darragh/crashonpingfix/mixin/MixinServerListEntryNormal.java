package me.darragh.crashonpingfix.mixin;

import com.google.common.base.Charsets;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.base64.Base64;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ServerListEntryNormal;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.IntBuffer;

@SuppressWarnings("UnusedMixin")
@Mixin(ServerListEntryNormal.class)
public class MixinServerListEntryNormal {
    @Final
    @Shadow
    private static Logger logger = LogManager.getLogger();

    @Final
    @Shadow
    private Minecraft mc;

    @Final
    @Shadow
    private ServerData server;

    @Final
    @Shadow
    private ResourceLocation serverIcon;

    @Shadow
    private DynamicTexture field_148305_h;

    /**
     * @author darraghd493
     * @reason Fixes the server icon loading process to prevent crashes when the server icon is invalid.
     */
    @Overwrite
    private void prepareServerIcon() {
        String iconData = this.server.getBase64EncodedIconData();
        if (iconData == null) {
            this.mc.getTextureManager().deleteTexture(this.serverIcon);
            this.field_148305_h = null;
        } else {
            ByteBuf decodedBuffer = Unpooled.copiedBuffer(iconData, Charsets.UTF_8);
            ByteBuf decodedImage;

            // Decode the image
            try {
                decodedImage = Base64.decode(decodedBuffer);
            } catch (Exception e) {
                logger.error("Failed to decode icon for server {} ({})", this.server.serverName, this.server.serverIP, e);
                this.server.setBase64EncodedIconData(null);
                return;
            } finally {
                decodedBuffer.release();
            }

            // Prepare the image
            BufferedImage image;
            int width, height;

            try (InputStream imageStream = new ByteBufInputStream(decodedImage)) {
                image = ImageIO.read(imageStream);
                width = image.getWidth();
                height = image.getHeight();

                Validate.validState(width == 64, "Must be 64 pixels wide");
                Validate.validState(height == 64, "Must be 64 pixels high");
            } catch (Exception e) {
                logger.error("Failed to read icon for server {} ({})", this.server.serverName, this.server.serverIP, e);
                this.server.setBase64EncodedIconData(null);
                return;
            } finally {
                decodedImage.release();
            }

            // Validate the image RGB content
            IntBuffer imageBuffer = IntBuffer.allocate(width * height);
            try {
                image.getRGB(0, 0, width, height, imageBuffer.array(), 0, width);
            } catch (ArrayIndexOutOfBoundsException e) {
                logger.error("Invalid icon for server {} ({})", this.server.serverName, this.server.serverIP, e);
                this.server.setBase64EncodedIconData(null);
                return;
            }

            // Initialise the texture
            if (this.field_148305_h == null) {
                this.field_148305_h = new DynamicTexture(width, height);
                this.mc.getTextureManager().loadTexture(this.serverIcon, this.field_148305_h);
            }

            // Upload the image data to the texture
            while (imageBuffer.hasRemaining()) {
                this.field_148305_h.getTextureData()[imageBuffer.position()] = imageBuffer.get();
            }
            this.field_148305_h.updateDynamicTexture();
        }
    }
}
