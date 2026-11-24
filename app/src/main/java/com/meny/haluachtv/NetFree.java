package com.meny.haluachtv;

import android.content.Context;
import android.net.http.SslCertificate;
import android.net.http.SslError;
import android.os.Build;

import java.io.InputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** בדיקה שתעודת אתר נחתמה ע"י אחת מתעודות השורש של נטפרי שבתוך האפליקציה. */
final class NetFree {
    private static List<X509Certificate> cas;

    private static synchronized List<X509Certificate> cas(Context c) {
        if (cas != null) return cas;
        cas = new ArrayList<>();
        try (InputStream in = c.getResources().openRawResource(R.raw.netfree_cas)) {
            Collection<? extends Certificate> all = CertificateFactory.getInstance("X.509").generateCertificates(in);
            for (Certificate x : all) if (x instanceof X509Certificate) cas.add((X509Certificate) x);
        } catch (Exception ignored) { }
        return cas;
    }

    static boolean trusted(Context c, SslError e) {
        try {
            SslCertificate sc = e.getCertificate();
            if (sc == null) return false;
            if (Build.VERSION.SDK_INT >= 29) {
                X509Certificate leaf = sc.getX509Certificate();
                if (leaf == null) return false;
                for (X509Certificate ca : cas(c)) {
                    try {
                        if (leaf.getIssuerX500Principal().equals(ca.getSubjectX500Principal())) {
                            leaf.verify(ca.getPublicKey());
                            leaf.checkValidity();
                            return true;
                        }
                    } catch (Exception ignored) { }
                }
                // חתימה דרך תעודת ביניים של נטפרי
                String issuer = leaf.getIssuerX500Principal().getName().toLowerCase();
                return issuer.contains("netfree") && !cas(c).isEmpty();
            }
            SslCertificate.DName by = sc.getIssuedBy();
            String s = (by.getDName() + " " + by.getOName() + " " + by.getCName()).toLowerCase();
            return s.contains("netfree");
        } catch (Exception ex) {
            return false;
        }
    }
}
