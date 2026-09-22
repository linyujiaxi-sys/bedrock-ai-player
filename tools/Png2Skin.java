import cn.nukkit.entity.data.Skin;
import cn.nukkit.utils.SerializedImage;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * PNG 皮肤 -> Nukkit/Bedrock 认可的 SkinData
 *
 * 为什么要转换：
 *   Nukkit 的 ClientChainData.getImage() 会直接 Base64 解码 SkinData，
 *   然后按【原始 RGBA 像素长度】校验（64x64 必须 = 16384 字节）。
 *   如果直接传 PNG 文件（几百~几千字节），fromLegacy 会抛
 *   "Unknown legacy skin size"，服务器就以 invalidSkin 踢人。
 *
 * 用法：java -cp nukkit.jar Png2Skin.java <in.png> <out.skin>
 * 输出格式：第一行 "宽 高"，第二行 base64(RGBA)
 */
public class Png2Skin {
    public static void main(String[] args) throws Exception {
        BufferedImage bi = ImageIO.read(new File(args[0]));
        if (bi == null) { System.err.println("无法读取 PNG: " + args[0]); System.exit(1); }

        // ★ 关键：交给 Nukkit 自己转换，保证格式与它期望的完全一致
        Skin s = new Skin();
        s.setSkinData(bi);
        SerializedImage img = s.getSkinData();

        System.out.println("PNG " + bi.getWidth() + "x" + bi.getHeight()
                + "  ->  SerializedImage " + img.width + "x" + img.height
                + ", data=" + img.data.length + " bytes");

        String b64 = Base64.getEncoder().encodeToString(img.data);
        String out = img.width + " " + img.height + "\n" + b64 + "\n";
        Files.write(Paths.get(args[1]), out.getBytes("UTF-8"));
        System.out.println("已写出 " + args[1] + " (" + b64.length() + " chars base64)");
    }
}