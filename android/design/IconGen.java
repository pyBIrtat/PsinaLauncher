import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/** Режет присланную иконку 1240x1240 в mipmap-<density>/ic_launcher.png */
public class IconGen {
    public static void main(String[] a) throws Exception {
        File src = new File(a[0]);          // исходный PNG
        File res = new File(a[1]);          // .../app/src/main/res
        BufferedImage img = ImageIO.read(src);
        if (img == null) throw new IllegalStateException("не читается: " + src);
        String[][] dens = {
                {"mipmap-mdpi", "48"},
                {"mipmap-hdpi", "72"},
                {"mipmap-xhdpi", "96"},
                {"mipmap-xxhdpi", "144"},
                {"mipmap-xxxhdpi", "192"},
        };
        for (String[] d : dens) {
            int px = Integer.parseInt(d[1]);
            File dir = new File(res, d[0]);
            dir.mkdirs();
            for (String name : new String[]{"ic_launcher.png", "ic_launcher_round.png"}) {
                BufferedImage out = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = out.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.drawImage(img, 0, 0, px, px, null);
                g.dispose();
                ImageIO.write(out, "png", new File(dir, name));
            }
            System.out.println("ok " + d[0] + " " + px + "px");
        }
        // 512px для стора/README
        BufferedImage big = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = big.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, 512, 512, null);
        g.dispose();
        ImageIO.write(big, "png", new File(a[2]));
        System.out.println("ok store icon " + a[2]);
    }
}
