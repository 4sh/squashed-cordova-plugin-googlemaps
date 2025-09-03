package plugin.google.maps;

import android.os.Bundle;
import android.util.Xml;

import org.apache.cordova.CallbackContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;


public class PluginKmlOverlay extends MyPlugin implements MyPluginInterface {
  private HashMap<String, Bundle> styles = new HashMap<String, Bundle>();

  private enum KML_TAG {
    NOT_SUPPORTED,
    kml,
    style,
    styleurl,
    stylemap,
    schema,
    coordinates
  }

  /**
   * Create kml overlay
   *
   * @param args
   * @param callbackContext
   * @throws JSONException
   */
  public void create(final JSONArray args, final CallbackContext callbackContext) throws JSONException {

    final JSONObject opts = args.getJSONObject(1);
    self = this;
    if (!opts.has("url")) {
      callbackContext.error("No kml file is specified");
      return;
    }

    cordova.getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {

        String urlStr = null;

        try {
          urlStr = opts.getString("url");
        } catch (JSONException e) {
          e.printStackTrace();
        }
        if (urlStr == null || urlStr.length() == 0) {
          callbackContext.error("No kml file is specified");
          return;
        }

        String currentPageUrl = webView.getUrl();
        if (!urlStr.contains("://") &&
            !urlStr.startsWith("/") &&
            !urlStr.startsWith("www/") &&
            !urlStr.startsWith("data:image") &&
            !urlStr.startsWith("./") &&
            !urlStr.startsWith("../")) {
          urlStr = "./" + urlStr;
        }

        if (currentPageUrl.startsWith("http://localhost") ||
            currentPageUrl.startsWith("http://127.0.0.1")) {
          if (urlStr.contains("://")) {
            urlStr = urlStr.replaceAll("http://.+?/", "file:///android_asset/www/");
          } else {
            // Avoid WebViewLocalServer (because can not make a connection for some reason)
            urlStr = "file:///android_asset/www/".concat(urlStr);
          }
        }


        if (urlStr.startsWith("./")  || urlStr.startsWith("../")) {
          urlStr = urlStr.replace("././", "./");
          currentPageUrl = currentPageUrl.replaceAll("[^\\/]*$", "");
          urlStr = currentPageUrl + "/" + urlStr;
        }
        if (urlStr.startsWith("cdvfile://")) {
          urlStr = PluginUtil.getAbsolutePathFromCDVFilePath(webView.getResourceApi(), urlStr);
        }

        // Avoid WebViewLocalServer (because can not make a connection for some reason)
        if (urlStr.contains("http://localhost") || urlStr.contains("http://127.0.0.1")) {
          urlStr = urlStr.replaceAll("^http://[^\\/]+\\//", "file:///android_asset/www/");
        }


        final String finalUrl = urlStr;
        executorService.submit(new Runnable() {
          @Override
          public void run() {
            Bundle result = loadKml(finalUrl);
            callbackContext.success(PluginUtil.Bundle2Json(result));
          }
        });
      }
    });
  }

  private Bundle loadKml(String urlStr) {
    InputStream inputStream = getKmlContents(urlStr);
    if (inputStream == null) {
      return null;
    }
    
    try {
      XmlPullParser parser = Xml.newPullParser();
      parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
      parser.setInput(inputStream, null);

      KmlParserClass kmlParser = new KmlParserClass();
      Bundle root = kmlParser.parseXml(parser);
      
      Bundle result = new Bundle();
      result.putBundle("schemas", kmlParser.schemaHolder);
      result.putBundle("styles", kmlParser.styleHolder);
      result.putBundle("root", root);

      inputStream.close();
      return result;
    } catch (XmlPullParserException | IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  class KmlParserClass {
    public Bundle styleHolder = new Bundle();
    public Bundle schemaHolder = new Bundle();

    private Bundle parseXml(XmlPullParser parser) throws XmlPullParserException, IOException {
      Bundle result = new Bundle();
      
      // Skip to first START_TAG
      int eventType = parser.getEventType();
      while (eventType != XmlPullParser.START_TAG && eventType != XmlPullParser.END_DOCUMENT) {
        eventType = parser.next();
      }
      
      if (eventType == XmlPullParser.END_DOCUMENT) {
        return result;
      }
      
      return parseElement(parser);
    }
    
    private Bundle parseElement(XmlPullParser parser) throws XmlPullParserException, IOException {
      Bundle result = new Bundle();
      String tagName = parser.getName().toLowerCase();
      result.putString("tagName", tagName);
      
      KML_TAG kmlTag;
      try {
        kmlTag = KML_TAG.valueOf(tagName);
      } catch (Exception e) {
        kmlTag = KML_TAG.NOT_SUPPORTED;
      }
      
      // Parse attributes
      int attributeCount = parser.getAttributeCount();
      for (int i = 0; i < attributeCount; i++) {
        String attrName = parser.getAttributeName(i);
        String attrValue = parser.getAttributeValue(i);
        result.putString(attrName, attrValue);
      }
      
      switch (kmlTag) {
        case styleurl:
          String styleUrl = parseTextContent(parser);
          result.putString("styleId", styleUrl);
          break;
          
        case stylemap:
        case style:
          parseStyleElement(parser, result);
          break;
          
        case schema:
          parseSchemaElement(parser, result);
          break;
          
        case coordinates:
          parseCoordinatesElement(parser, result);
          break;
          
        default:
          parseGenericElement(parser, result, tagName);
          break;
      }
      
      return result;
    }
    
    private void parseStyleElement(XmlPullParser parser, Bundle result) throws XmlPullParserException, IOException {
      String styleId = parser.getAttributeValue(null, "id");
      if (styleId == null || styleId.isEmpty()) {
        styleId = "__generated_" + System.currentTimeMillis() + "__";
      }
      result.putString("styleId", styleId);
      
      Bundle styles = new Bundle();
      ArrayList<Bundle> children = new ArrayList<Bundle>();
      
      int eventType = parser.next();
      while (eventType != XmlPullParser.END_TAG || !parser.getName().equals(result.getString("tagName"))) {
        if (eventType == XmlPullParser.START_TAG) {
          Bundle childNode = parseElement(parser);
          if (childNode != null) {
            if (childNode.containsKey("value")) {
              styles.putString(childNode.getString("tagName"), childNode.getString("value"));
            } else {
              children.add(childNode);
            }
          }
        }
        eventType = parser.next();
      }
      
      if (children.size() > 0) {
        styles.putParcelableArrayList("children", children);
      }
      styleHolder.putBundle(styleId, styles);
    }
    
    private void parseSchemaElement(XmlPullParser parser, Bundle result) throws XmlPullParserException, IOException {
      String schemaId = parser.getAttributeValue(null, "id");
      if (schemaId == null || schemaId.isEmpty()) {
        schemaId = "__generated_" + System.currentTimeMillis() + "__";
      }
      
      Bundle schema = new Bundle();
      schema.putString("name", parser.getAttributeValue(null, "name"));
      ArrayList<Bundle> children = new ArrayList<Bundle>();
      
      int eventType = parser.next();
      while (eventType != XmlPullParser.END_TAG || !parser.getName().equals("schema")) {
        if (eventType == XmlPullParser.START_TAG) {
          Bundle childNode = parseElement(parser);
          if (childNode != null) {
            children.add(childNode);
          }
        }
        eventType = parser.next();
      }
      
      if (children.size() > 0) {
        schema.putParcelableArrayList("children", children);
      }
      schemaHolder.putBundle(schemaId, schema);
    }
    
    private void parseCoordinatesElement(XmlPullParser parser, Bundle result) throws XmlPullParserException, IOException {
      String coordinatesText = parseTextContent(parser);
      ArrayList<Bundle> latLngList = new ArrayList<Bundle>();
      
      coordinatesText = coordinatesText.replaceAll("\\s+", "\n");
      coordinatesText = coordinatesText.replaceAll("\\n+", "\n");
      String[] lines = coordinatesText.split("\n");
      
      for (String line : lines) {
        line = line.replaceAll("[^0-9,.\\-Ee]", "");
        if (!line.isEmpty()) {
          String[] coords = line.split(",");
          if (coords.length >= 2) {
            Bundle latLng = new Bundle();
            try {
              latLng.putDouble("lat", Double.parseDouble(coords[1]));
              latLng.putDouble("lng", Double.parseDouble(coords[0]));
              latLngList.add(latLng);
            } catch (NumberFormatException e) {
              // Skip invalid coordinates
            }
          }
        }
      }
      
      result.putParcelableArrayList("coordinates", latLngList);
    }
    
    private void parseGenericElement(XmlPullParser parser, Bundle result, String tagName) throws XmlPullParserException, IOException {
      int eventType = parser.next();
      ArrayList<Bundle> children = new ArrayList<Bundle>();
      StringBuilder textContent = new StringBuilder();
      
      while (eventType != XmlPullParser.END_TAG || !parser.getName().equals(tagName)) {
        if (eventType == XmlPullParser.START_TAG) {
          Bundle childNode = parseElement(parser);
          if (childNode != null) {
            if (childNode.containsKey("styleId")) {
              ArrayList<String> styleIDs = result.getStringArrayList("styleIDs");
              if (styleIDs == null) {
                styleIDs = new ArrayList<String>();
              }
              styleIDs.add(childNode.getString("styleId"));
              result.putStringArrayList("styleIDs", styleIDs);
            } else if (!"schema".equals(childNode.getString("tagName"))) {
              children.add(childNode);
            }
          }
        } else if (eventType == XmlPullParser.TEXT) {
          textContent.append(parser.getText());
        }
        eventType = parser.next();
      }
      
      if (children.size() > 0) {
        result.putParcelableArrayList("children", children);
      } else if (textContent.length() > 0) {
        result.putString("value", textContent.toString().trim());
      }
    }
    
    private String parseTextContent(XmlPullParser parser) throws XmlPullParserException, IOException {
      StringBuilder result = new StringBuilder();
      int eventType = parser.next();
      
      while (eventType != XmlPullParser.END_TAG) {
        if (eventType == XmlPullParser.TEXT) {
          result.append(parser.getText());
        }
        eventType = parser.next();
      }
      
      return result.toString().trim();
    }
  }


  private InputStream getKmlContents(String urlStr) {

    InputStream inputStream;
    try {
      //Log.d("PluginKmlOverlay", "---> url = " + urlStr);
      if (urlStr.startsWith("http://") || urlStr.startsWith("https://")) {
        URL url = new URL(urlStr);
        boolean redirect = true;
        HttpURLConnection http = null;
        String cookies = null;
        int redirectCnt = 0;
        while(redirect && redirectCnt < 10) {
          redirect = false;
          http = (HttpURLConnection)url.openConnection();
          http.setRequestMethod("GET");
          if (cookies != null) {
            http.setRequestProperty("Cookie", cookies);
          }
          http.addRequestProperty("Accept-Language", "en-US,en;q=0.8");
          http.addRequestProperty("User-Agent", "Mozilla");
          http.setInstanceFollowRedirects(true);
          HttpURLConnection.setFollowRedirects(true);

          // normally, 3xx is redirect
          int status = http.getResponseCode();
          if (status != HttpURLConnection.HTTP_OK) {
            if (status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_MOVED_PERM
                || status == HttpURLConnection.HTTP_SEE_OTHER)
              redirect = true;
          }
          if (redirect) {
            // get redirect url from "location" header field
            url = new URL(http.getHeaderField("Location"));

            // get the cookie if need, for login
            cookies = http.getHeaderField("Set-Cookie");

            // Disconnect the current connection
            http.disconnect();
            redirectCnt++;
          }
        }

        inputStream = http.getInputStream();
      } else if (urlStr.indexOf("file://") == 0 && !urlStr.contains("file:///android_asset/") ||
          urlStr.indexOf("/") == 0) {
        urlStr = urlStr.replace("file://", "");
        try {
          boolean isAbsolutePath = urlStr.startsWith("/");
          File relativePath = new File(urlStr);
          urlStr = relativePath.getCanonicalPath();
          //Log.d(TAG, "imgUrl = " + imgUrl);
          if (!isAbsolutePath) {
            urlStr = urlStr.substring(1);
          }
          //Log.d(TAG, "imgUrl = " + imgUrl);
        } catch (Exception e) {
          e.printStackTrace();
        }
        //Log.d("PluginKmlOverlay", "---> url = " + urlStr);
        inputStream = new FileInputStream(urlStr);
      } else {
        if (urlStr.indexOf("file:///android_asset/") == 0) {
          urlStr = urlStr.replace("file:///android_asset/", "");
        }


        try {
          boolean isAbsolutePath = urlStr.startsWith("/");
          File relativePath = new File(urlStr);
          urlStr = relativePath.getCanonicalPath();
          //Log.d(TAG, "imgUrl = " + imgUrl);
          if (!isAbsolutePath) {
            urlStr = urlStr.substring(1);
          }
          //Log.d(TAG, "imgUrl = " + imgUrl);
        } catch (Exception e) {
          e.printStackTrace();
        }
        //Log.d("PluginKmlOverlay", "---> url = " + urlStr);
        inputStream = cordova.getActivity().getResources().getAssets().open(urlStr);
      }

    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }

    return inputStream;

  }

}
