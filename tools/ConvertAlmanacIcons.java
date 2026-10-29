import java.nio.file.*;
import com.android.ide.common.vectordrawable.Svg2Vector;

/** Run with Android SDK's sdk-common/common and Guava jars on the classpath. */
class ConvertAlmanacIcons {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[1]);
        Files.createDirectories(output);
        try (var files = Files.list(Path.of(args[0]))) {
            for (Path svg : files.filter(p -> p.toString().endsWith(".svg")).toList()) {
                // SVG currentColor has no equivalent in the import tool; use black then tint at runtime.
                Path input = Files.createTempFile("calendarmods-icon-", ".svg");
                try {
                    Files.writeString(input, Files.readString(svg).replace("currentColor", "#000000"));
                    Path xml = output.resolve("almanac_" + svg.getFileName().toString().replace('-', '_').replace(".svg", ".xml"));
                    try (var stream = Files.newOutputStream(xml)) {
                        String errors = Svg2Vector.parseSvgToXml(input, stream);
                        if (!errors.isBlank()) throw new IllegalStateException(svg + ": " + errors);
                    }
                    System.out.println(xml.getFileName());
                } finally { Files.deleteIfExists(input); }
            }
        }
    }
}
