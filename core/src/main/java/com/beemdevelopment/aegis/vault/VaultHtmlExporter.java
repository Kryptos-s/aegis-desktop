package com.beemdevelopment.aegis.vault;

import com.beemdevelopment.aegis.encoding.Base32;
import com.beemdevelopment.aegis.encoding.Base64;
import com.beemdevelopment.aegis.encoding.Hex;
import com.beemdevelopment.aegis.otp.GoogleAuthInfo;
import com.beemdevelopment.aegis.otp.HotpInfo;
import com.beemdevelopment.aegis.otp.MotpInfo;
import com.beemdevelopment.aegis.otp.OtpInfo;
import com.beemdevelopment.aegis.otp.YandexInfo;
import com.beemdevelopment.aegis.util.QrCodes;
import com.google.zxing.WriterException;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;

/**
 * Writes the vault as a self-contained HTML page with a QR code per entry, for printing. Every
 * secret ends up in the output in plaintext; warning the user about that is the caller's job.
 */
public class VaultHtmlExporter {
    private VaultHtmlExporter() {

    }

    public static void export(PrintStream ps, Collection<VaultEntry> entries, String title)
            throws WriterException, IOException {
        ps.print("<html><head><title>");
        ps.print(escape(title));
        ps.print("</title></head><body>");
        ps.print("<h1>");
        ps.print(escape(title));
        ps.print("</h1>");
        ps.print("<table>");
        ps.print("<tr>");
        ps.print("<th>Issuer</th>");
        ps.print("<th>Name</th>");
        ps.print("<th>Type</th>");
        ps.print("<th>QR Code</th>");
        ps.print("<th>UUID</th>");
        ps.print("<th>Note</th>");
        ps.print("<th>Favorite</th>");
        ps.print("<th>Algo</th>");
        ps.print("<th>Digits</th>");
        ps.print("<th>Secret</th>");
        ps.print("<th>Counter</th>");
        ps.print("<th>PIN</th>");
        ps.print("</tr>");
        for (VaultEntry entry : entries) {
            ps.print("<tr>");
            OtpInfo info = entry.getInfo();
            GoogleAuthInfo gaInfo = new GoogleAuthInfo(info, entry.getName(), entry.getIssuer());
            appendRow(ps, entry.getIssuer());
            appendRow(ps, entry.getName());
            appendRow(ps, info.getType());
            appendQrRow(ps, gaInfo.getUri().toString());
            appendRow(ps, entry.getUUID().toString());
            appendRow(ps, entry.getNote());
            appendRow(ps, Boolean.toString(entry.isFavorite()));
            appendRow(ps, info.getAlgorithm(false));
            appendRow(ps, Integer.toString(info.getDigits()));
            if (info instanceof MotpInfo) {
                appendRow(ps, Hex.encode(info.getSecret()));
            } else {
                appendRow(ps, Base32.encode(info.getSecret()));
            }
            if (info instanceof HotpInfo) {
                appendRow(ps, Long.toString(((HotpInfo) info).getCounter()));
            } else {
                appendRow(ps, "-");
            }
            if (info instanceof YandexInfo) {
                appendRow(ps, ((YandexInfo) info).getPin());
            } else if (info instanceof MotpInfo) {
                appendRow(ps, ((MotpInfo) info).getPin());
            } else {
                appendRow(ps, "-");
            }
            ps.print("</tr>");
        }
        ps.print("</table></body>");
        ps.print("<style>table,td,th{border:1px solid #000;border-collapse:collapse;text-align:center}td:not(.qr),th{padding:1em}</style>");
        ps.print("</html>");
    }

    private static void appendRow(PrintStream ps, String s) {
        ps.print("<td>");
        ps.print(escape(s));
        ps.print("</td>");
    }

    private static void appendQrRow(PrintStream ps, String s) throws IOException, WriterException {
        ps.print("<td class='qr'><img src=\"data:image/png;base64,");
        ps.print(Base64.encode(QrCodes.encodeToPng(s, 256, 256, QrCodes.WHITE)));
        ps.print("\"/></td>");
    }

    /** Names, issuers and notes come from scanned QR codes, so nothing reaches the output raw. */
    private static String escape(String s) {
        if (s == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&#39;");
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }

        return sb.toString();
    }
}
