import cn.nukkit.entity.data.Skin;
import cn.nukkit.utils.SerializedImage;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.lang.reflect.Method;

/** 用 Nukkit 自己的类验证皮肤数据 —— 定位到底哪一项不合格 */
public class TestSkin {
    static Object call(Object o, String name) throws Exception {
        Method m = o.getClass().getDeclaredMethod(name);
        m.setAccessible(true);
        return m.invoke(o);
    }

    public static void main(String[] args) throws Exception {
        byte[] png = Files.readAllBytes(Paths.get("/storage/emulated/0/Download/mc-bot/skin/deepseek.png"));
        System.out.println("PNG 字节数 = " + png.length);

        SerializedImage img = SerializedImage.fromLegacy(png);
        System.out.println("SerializedImage = " + img.width + " x " + img.height + ", data=" + img.data.length);

        String geo = "{\"format_version\":\"1.12.0\",\"minecraft:geometry\":[{\"description\":{\"identifier\":\"geometry.humanoid.custom\",\"texture_width\":64,\"texture_height\":64,\"visible_bounds_width\":2,\"visible_bounds_height\":3,\"visible_bounds_offset\":[0,1.5,0]},\"bones\":[]}]}";
        String rp = "{\"geometry\":{\"default\":\"geometry.humanoid.custom\"}}";

        Skin s = new Skin();
        s.setSkinId("deepseek-custom-skin");
        s.setSkinData(img);
        s.setGeometryData(geo);
        s.setSkinResourcePatch(rp);
        s.setGeometryDataEngineVersion("1.14.0");
        s.setPersona(false);
        s.setPremium(false);
        s.setCapeData(SerializedImage.EMPTY);

        System.out.println();
        System.out.println("--- 逐项检查 ---");
        try { System.out.println("isValidSkin          = " + call(s, "isValidSkin")); } catch (Throwable t) { System.out.println("isValidSkin ERR " + t); }
        try { System.out.println("isValidResourcePatch = " + call(s, "isValidResourcePatch")); } catch (Throwable t) { System.out.println("isValidResourcePatch ERR " + t); }
        try { System.out.println("isValidGeometry      = " + call(s, "isValidGeometry")); } catch (Throwable t) { System.out.println("isValidGeometry ERR " + t); }
        System.out.println();
        System.out.println(">>> isValid = " + s.isValid());
    }
}
