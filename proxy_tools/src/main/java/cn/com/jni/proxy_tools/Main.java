package cn.com.jni.proxy_tools;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class Main {

    private static final String ANDROID_TOOLS_PATH = "/Users/yangpengliang/Library/Android/sdk/build-tools/28.0.3/";
    public static void main(String[] args) throws Exception {

        /**
         * 1、制作只包含解密代码的dex文件
         */
        File aarFile = new File("proxy_core/build/outputs/aar/proxy_core-debug.aar");
        File aarTemp = new File("proxy_tools/temp");
        Zip.unZip(aarFile, aarTemp);

        File classesDex = new File(aarTemp, "classes.dex");
        File classesJar = new File(aarTemp, "classes.jar");
        //dx --dex --output out.dex in.jar     E:\AndroidSdk\Sdk\build-tools\23.0.3
        Process processdx;
        if (System.getProperty("os.name").contains("Mac")) {
            //Users/yangpengliang/Library/Android/sdk/build-tools/30.0.0/dx
            processdx = Runtime.getRuntime().exec(ANDROID_TOOLS_PATH  + "dx --dex --output " + classesDex.getAbsolutePath()
                    + " " + classesJar.getAbsolutePath());
        } else {
            processdx = Runtime.getRuntime().exec("cmd /c dx --dex --output " + classesDex.getAbsolutePath()
                    + " " + classesJar.getAbsolutePath());
        }


        processdx.waitFor();
        if (processdx.exitValue() != 0) {
            printProcessError(processdx);
            throw new RuntimeException("processdx dex error");
        }
        processdx.destroy();

        /**
         * 2、加密apk中所有的dex文件
         */
        File apkFile = new File("app/build/outputs/apk/debug/app-debug.apk");
        File apkTemp = new File("app/build/outputs/apk/debug/temp");
        Zip.unZip(apkFile, apkTemp);
        //只要dex文件拿出来加密
        File[] dexFiles = apkTemp.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                return s.endsWith(".dex");
            }
        });
        //RC4Base 加密

        for (File dexFile : dexFiles) {
            byte[] bytes = Utils.getBytes(dexFile);
            //  byte[] encrypt = AES.encrypt(bytes);
            byte[] encrypt = RC4.RC4Base(bytes, RC4.ACCESSKEY);
            FileOutputStream fos = new FileOutputStream(new File(apkTemp, "secret-" + dexFile.getName()));
            fos.write(encrypt);
            fos.flush();
            fos.close();
            dexFile.delete();
        }
        /**
         * 3、把dex放入apk解压目录，重新压成apk文件
         */
        classesDex.renameTo(new File(apkTemp, "classes.dex"));
        File unSignedApk = new File("app/build/outputs/apk/debug/app-unsigned.apk");
        if(unSignedApk.exists()){
            unSignedApk.delete();
        }
        Zip.zip(apkTemp, unSignedApk);
        /**
         * 4、对其和签名，最后生成签名apk
         */
        //        zipalign -v -p 4 my-app-unsigned.apk my-app-unsigned-aligned.apk
        File alignedApk = new File("app/build/outputs/apk/debug/app-unsigned-aligned.apk");
        if(alignedApk.exists()){
            alignedApk.delete();
        }
        Process processAlign;
        if (System.getProperty("os.name").contains("Mac")) {
            String cmd = ANDROID_TOOLS_PATH  + "zipalign -v -p 4 " + unSignedApk.getAbsolutePath()
                    + " " + alignedApk.getAbsolutePath();
            processAlign = Runtime.getRuntime().exec(cmd);
        } else {
            processAlign = Runtime.getRuntime().exec("cmd /c zipalign -v -p 4 " + unSignedApk.getAbsolutePath()
                    + " " + alignedApk.getAbsolutePath());
        }

//        System.out.println("signedApkprocess : 11111" + "  :----->  " +unSignedApk.getAbsolutePath() + "\n" +  alignedApk.getAbsolutePath());

        processAlign.waitFor(10, TimeUnit.SECONDS);
        if (processAlign.exitValue() != 0) {
            printProcessError(processAlign);
            throw new RuntimeException("processAlign error");
        }
        processAlign.destroy();

//        apksigner sign --ks my-release-key.jks --out my-app-release.apk my-app-unsigned-aligned.apk
//        apksigner sign  --ks jks文件地址 --ks-key-alias 别名 --ks-pass pass:jsk密码 --key-pass pass:别名密码 --out  out.apk in.apk
        File signedApk = new File("app/build/outputs/apk/debug/app-signed-aligned.apk");
        if(signedApk.exists()){
            signedApk.delete();
        }
        File jks = new File("proxy_tools/proxy1.jks");
        Process processsign;
        if (System.getProperty("os.name").contains("Mac")) {
            String cmd = ANDROID_TOOLS_PATH  + "apksigner sign --ks " + jks.getAbsolutePath()
                    + " --ks-key-alias wwy --ks-pass pass:123456 --key-pass pass:123456 --out "
                    + signedApk.getAbsolutePath() + " " + alignedApk.getAbsolutePath();
            processsign = Runtime.getRuntime().exec(cmd);
        } else {
            processsign = Runtime.getRuntime().exec("cmd /c apksigner sign --ks " + jks.getAbsolutePath()
                    + " --ks-key-alias wwy --ks-pass pass:123456 --key-pass pass:123456 --out "
                    + signedApk.getAbsolutePath() + " " + alignedApk.getAbsolutePath());
        }

        processsign.waitFor();
        if (processsign.exitValue() != 0) {
            printProcessError(processsign);
            throw new RuntimeException("processsign error");
        }
        processsign.destroy();
        System.out.println("excute successful");
    }

    /**
     * @param processsign
     */
    private static void printProcessError(Process processsign) {
        try{
            InputStream errorStream = processsign.getErrorStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream));
            String line = null;
            while((line = reader.readLine()) != null){
                System.out.println(line);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}