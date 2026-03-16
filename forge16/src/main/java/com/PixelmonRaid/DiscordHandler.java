package com.PixelmonRaid;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class DiscordHandler {
   public static void sendEmbed(String title, String description, int color) {
      String webhookUrl = PixelmonRaidConfig.getInstance().getDiscordWebhookUrl();
      if (webhookUrl != null && !webhookUrl.isEmpty() && webhookUrl.startsWith("http")) {
         CompletableFuture.runAsync(() -> {
            try {
               URL url = new URL(webhookUrl);
               HttpURLConnection conn = (HttpURLConnection)url.openConnection();
               conn.setRequestMethod("POST");
               conn.setRequestProperty("Content-Type", "application/json");
               conn.setRequestProperty("User-Agent", "PixelmonRaidMod");
               conn.setDoOutput(true);
               String json = "{\"embeds\": [{\"title\": \"" + escape(title) + "\",\"description\": \"" + escape(description) + "\",\"color\": " + color + "}]}";
               OutputStream os = conn.getOutputStream();
               Throwable var8 = null;

               try {
                  byte[] input = json.getBytes(StandardCharsets.UTF_8);
                  os.write(input, 0, input.length);
               } catch (Throwable var18) {
                  var8 = var18;
                  throw var18;
               } finally {
                  if (os != null) {
                     if (var8 != null) {
                        try {
                           os.close();
                        } catch (Throwable var17) {
                           var8.addSuppressed(var17);
                        }
                     } else {
                        os.close();
                     }
                  }

               }

               conn.getResponseCode();
               conn.disconnect();
            } catch (Exception var20) {
               var20.printStackTrace();
            }

         });
      }
   }

   private static String escape(String str) {
      return str.replace("\"", "\\\"").replace("\n", "\\n");
   }
}
