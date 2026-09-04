package com.bifrost.core.book;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 测试用图书样本工厂：用 {@link EpubWriter} 直接生成合法 EPUB（避免手工 ZIP 容器与
 * epublib 内部 {@code jazzlib} 解析不兼容导致无限循环）；PDF 用 PDFBox 写极小文件。
 */
final class TestBookFactory {

    private TestBookFactory() {
    }

    /** 元数据规格 */
    record BookSpec(String title, List<String> authors, String language, String publisher,
                    String date, String description, List<String> subjects, String identifier,
                    String series, String seriesIndex, String rights, byte[] coverJpeg) {
    }

    /** 极小有效 JPEG（1x1 灰度，67 字节） */
    static byte[] tinyJpeg() {
        return new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F', 0x00,
                0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
                (byte) 0xFF, (byte) 0xDB, 0x00, 0x43, 0x00,
                8, 6, 6, 7, 6, 5, 8, 7, 7, 7, 9, 9, 8, 10, 12, 20, 13, 12, 11, 11, 12, 25, 18, 19, 15, 20, 29, 26, 31, 30, 29, 26, 28, 28, 32, 36, 46, 39, 32, 34, 44, 35, 28, 28, 40, 55, 41, 44, 48, 49, 52, 52, 52, 31, 39, 57, 61, 56, 50, 60, 46, 51, 52, 50,
                (byte) 0xFF, (byte) 0xC0, 0x00, 0x0B, 0x08, 0x00, 0x01, 0x00, 0x01, 0x01, 0x01, 0x11, 0x00,
                (byte) 0xFF, (byte) 0xC4, 0x00, 0x1F, 0x00,
                0x00, 0x01, 0x05, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B,
                (byte) 0xFF, (byte) 0xC4, 0x00, (byte) 0xB5, 0x10,
                0x00, 0x02, 0x01, 0x03, 0x03, 0x02, 0x04, 0x03, 0x05, 0x05, 0x04, 0x04, 0x00, 0x00, 0x01, 0x7D, 0x01, 0x02, 0x03, 0x00, 0x04, 0x11, 0x05, 0x12, 0x21, 0x31, 0x41, 0x06, 0x13, 0x51, 0x61,
                0x07, 0x22, 0x71, 0x14, 0x32, (byte) 0x81, (byte) 0x91, (byte) 0xA1, 0x08, 0x23, 0x42, (byte) 0xB1, (byte) 0xC1, 0x15, 0x52, (byte) 0xD1, (byte) 0xF0, 0x24, 0x33, 0x62, 0x72, (byte) 0x82,
                0x09, 0x0A, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x25, 0x26, 0x27, 0x28, 0x29, 0x2A, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3A, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4A, 0x53, 0x54, 0x55, 0x56,
                0x57, 0x58, 0x59, 0x5A, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6A, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7A, (byte) 0x83, (byte) 0x84, (byte) 0x85, (byte) 0x86, (byte) 0x87, (byte) 0x88,
                (byte) 0x89, (byte) 0x8A, (byte) 0x92, (byte) 0x93, (byte) 0x94, (byte) 0x95, (byte) 0x96, (byte) 0x97, (byte) 0x98, (byte) 0x99, (byte) 0x9A, (byte) 0xA2, (byte) 0xA3, (byte) 0xA4, (byte) 0xA5, (byte) 0xA6,
                (byte) 0xA7, (byte) 0xA8, (byte) 0xA9, (byte) 0xAA, (byte) 0xB2, (byte) 0xB3, (byte) 0xB4, (byte) 0xB5, (byte) 0xB6, (byte) 0xB7, (byte) 0xB8, (byte) 0xB9, (byte) 0xBA, (byte) 0xC2, (byte) 0xC3, (byte) 0xC4,
                (byte) 0xC5, (byte) 0xC6, (byte) 0xC7, (byte) 0xC8, (byte) 0xC9, (byte) 0xCA, (byte) 0xD2, (byte) 0xD3, (byte) 0xD4, (byte) 0xD5, (byte) 0xD6, (byte) 0xD7, (byte) 0xD8, (byte) 0xD9, (byte) 0xDA, (byte) 0xE1,
                (byte) 0xE2, (byte) 0xE3, (byte) 0xE4, (byte) 0xE5, (byte) 0xE6, (byte) 0xE7, (byte) 0xE8, (byte) 0xE9, (byte) 0xEA, (byte) 0xF1, (byte) 0xF2, (byte) 0xF3, (byte) 0xF4, (byte) 0xF5, (byte) 0xF6, (byte) 0xF7,
                (byte) 0xF8, (byte) 0xF9, (byte) 0xFA,
                (byte) 0xFF, (byte) 0xDA, 0x00, 0x08, 0x01, 0x01, 0x00, 0x00, 0x3F, 0x00, (byte) 0xFB, (byte) 0xD0, (byte) 0xFF, (byte) 0xD9
        };
    }

    /**
     * 生成合法 EPUB：用 {@link java.util.zip.ZipOutputStream} 写标准 ZIP 容器，
     * {@code mimetype} 为 STORED 第一项，其余 DEFLATED。
     * <p>
     * 重要：epublib 自带的 {@code net.sf.jazzlib.ZipInputStream} 在读
     * {@code EpubWriter} 写的文件时存在循环问题（已实测：会卡在 "EOF in header"）。
     * 改用 JDK 标准 ZIP 输出可被 epublib 正常解析。
     * </p>
     */
    static Path writeEpub(Path dir, String fileName, BookSpec spec) throws IOException {
        Path file = dir.resolve(fileName);
        byte[] mimetypeBytes = "application/epub+zip".getBytes(StandardCharsets.US_ASCII);
        try (var fos = Files.newOutputStream(file);
             var zos = new ZipOutputStream(fos)) {

            // mimetype: 必须 STORED, 第一项
            ZipEntry mimetype = new ZipEntry("mimetype");
            mimetype.setMethod(ZipEntry.STORED);
            mimetype.setSize(mimetypeBytes.length);
            mimetype.setCrc(crc32(mimetypeBytes));
            zos.putNextEntry(mimetype);
            zos.write(mimetypeBytes);
            zos.closeEntry();

            // META-INF/container.xml
            zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
            String containerXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n"
                    + "  <rootfiles>\n"
                    + "    <rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>\n"
                    + "  </rootfiles>\n"
                    + "</container>\n";
            zos.write(containerXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // OEBPS/content.opf
            StringBuilder opf = new StringBuilder();
            opf.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                    .append("<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"bookid\" xml:lang=\"").append(esc(spec.language())).append("\">\n")
                    .append("  <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n")
                    .append("    <dc:identifier id=\"bookid\">").append(esc(spec.identifier())).append("</dc:identifier>\n")
                    .append("    <dc:title>").append(esc(spec.title())).append("</dc:title>\n")
                    .append("    <dc:language>").append(esc(spec.language())).append("</dc:language>\n");
            for (String a : spec.authors()) {
                opf.append("    <dc:creator id=\"creator-").append(esc(a)).append("\">").append(esc(a)).append("</dc:creator>\n");
            }
            if (spec.publisher() != null) {
                opf.append("    <dc:publisher>").append(esc(spec.publisher())).append("</dc:publisher>\n");
            }
            if (spec.date() != null) {
                opf.append("    <dc:date>").append(esc(spec.date())).append("</dc:date>\n");
            }
            if (spec.description() != null) {
                opf.append("    <dc:description>").append(esc(spec.description())).append("</dc:description>\n");
            }
            for (String s : spec.subjects()) {
                opf.append("    <dc:subject>").append(esc(s)).append("</dc:subject>\n");
            }
            if (spec.rights() != null) {
                opf.append("    <dc:rights>").append(esc(spec.rights())).append("</dc:rights>\n");
            }
            if (spec.series() != null) {
                opf.append("    <meta property=\"belongs-to-collection\" id=\"c1\">").append(esc(spec.series())).append("</meta>\n");
                if (spec.seriesIndex() != null) {
                    opf.append("    <meta refines=\"#c1\" property=\"group-position\">").append(esc(spec.seriesIndex())).append("</meta>\n");
                }
            }
            boolean hasCover = spec.coverJpeg() != null;
            if (hasCover) {
                // epublib 3.1 不识别 EPUB3 properties="cover-image"，必须显式声明 <meta name="cover">
                opf.append("    <meta name=\"cover\" content=\"cover-img\"/>\n");
            }
            opf.append("  </metadata>\n")
                    .append("  <manifest>\n")
                    .append("    <item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>\n");
            if (hasCover) {
                opf.append("    <item id=\"cover-img\" href=\"cover.jpg\" media-type=\"image/jpeg\" properties=\"cover-image\"/>\n");
            }
            opf.append("  </manifest>\n")
                    .append("  <spine>\n")
                    .append("    <itemref idref=\"nav\"/>\n")
                    .append("  </spine>\n")
                    .append("</package>\n");

            zos.putNextEntry(new ZipEntry("OEBPS/content.opf"));
            zos.write(opf.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // OEBPS/nav.xhtml
            zos.putNextEntry(new ZipEntry("OEBPS/nav.xhtml"));
            String navHead = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\">\n"
                    + "  <body><nav epub:type=\"toc\"><h1>";
            zos.write(navHead.getBytes(StandardCharsets.UTF_8));
            zos.write(spec.title().getBytes(StandardCharsets.UTF_8));
            zos.write("</h1></nav></body></html>\n".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            if (hasCover) {
                zos.putNextEntry(new ZipEntry("OEBPS/cover.jpg"));
                zos.write(spec.coverJpeg());
                zos.closeEntry();
            }
        }
        return file;
    }

    private static long crc32(byte[] data) {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(data);
        return crc.getValue();
    }

    /** 极简 PDF（PDF 1.4 单页），可被 PDFBox Loader.loadPDF 解析。 */
    static Path writeMinimalPdf(Path dir, String fileName) throws IOException {
        String pdf = "%PDF-1.4\n"
                + "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"
                + "2 0 obj\n<< /Type /Pages /Count 1 /Kids [3 0 R] >>\nendobj\n"
                + "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>\nendobj\n"
                + "xref\n0 4\n0000000000 65535 f \n0000000009 00000 n \n0000000058 00000 n \n0000000110 00000 n \n"
                + "trailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n163\n%%EOF\n";
        Path file = dir.resolve(fileName);
        Files.writeString(file, pdf, StandardCharsets.ISO_8859_1);
        return file;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}