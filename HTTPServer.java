import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import com.sun.net.httpserver.*;

public class HTTPServer {

    private static final String FILE_ROOT = "resources";
    private static final HashMap<String, String> SUPPORTED_MIME_TYPES = new HashMap<>(){{
        put("txt", "text/plain");
        put("html", "text/html");
        put("json", "text/json");
    }};

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", new Handler());
        server.setExecutor(null);
        server.start();
        System.out.println("Server is listening on port 8080");
    }
    private static class Handler implements HttpHandler {
        
        @Override
        public void handle(HttpExchange t) throws IOException {
            // get urls and stuff
            String urlPath = t.getRequestURI().getPath();
            Path filePath = Paths.get(FILE_ROOT + urlPath);
            File file = filePath.toFile();
            
            // get file information
            String[] temp = urlPath.split("\\.");
            if (temp.length != 2) {
                t.sendResponseHeaders(415, 0);
                return;
            }
            String extension = temp[1];

            String requestMethod = t.getRequestMethod();
            switch(requestMethod) {
                case "GET":
                    if (!fileExists(t, file)) break;
                    // check if file extension is supported
                    if (!SUPPORTED_MIME_TYPES.containsKey(extension)) {
                        t.sendResponseHeaders(415, 0);
                        break;
                    }
                    send(t, filePath);
                    break;
                case "POST":
                    if (!fileExists(t, file)) break;
                    // check if file to be editted is in plain text
                    if (!mime(filePath).equals("text/plain")) {
                        t.sendResponseHeaders(415, 0);
                        break;
                    }
                    editFile(t, FILE_ROOT + urlPath);
                    send(t, filePath);
                    break;
                case "PUT":
                    // check if file extension is supported
                    if (!SUPPORTED_MIME_TYPES.containsKey(extension)) {
                        t.sendResponseHeaders(415, 0);
                        break;
                    }
                    overrideFile(t, FILE_ROOT + urlPath);
                    send(t, filePath);
                    break;
                case "DELETE":
                    if (!fileExists(t, file)) break;
                    deleteFile(file);
                    t.sendResponseHeaders(200, 0);
                    break;
                default:
                    t.sendResponseHeaders(405, 0);
            }
            t.close();
        }

        // returns true if file exists
        private boolean fileExists(HttpExchange t, File file) throws IOException {
            if (!file.exists() || !file.canRead()) {
                t.sendResponseHeaders(404, 0);
                return false;
            }
            return true;
        }

        // sends contents of example.txt
        private void send(HttpExchange t, Path filePath) throws IOException {
            byte[] bytes = Files.readAllBytes(filePath);
            t.sendResponseHeaders(200, bytes.length);
            OutputStream os = t.getResponseBody();
            os.write(bytes);
            os.close();
        }

        // checks MIME type
        private String mime(Path filePath) throws IOException {
            return Files.probeContentType(filePath);
        }

        // appends a new line to contents of existing file with http body
        private void editFile(HttpExchange t, String filePath) throws IOException {

            // get file
            FileWriter fileWriter = new FileWriter(filePath, true);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);

            // read request body
            InputStreamReader isr =  new InputStreamReader(t.getRequestBody(),"utf-8");
            BufferedReader br = new BufferedReader(isr);

            // request body to string
            int b;
            StringBuilder buf = new StringBuilder();
            while ((b = br.read()) != -1) {
                buf.append((char) b);
            }
            br.close();
            isr.close();

            // write to file
            bufferedWriter.write(buf.toString());
            bufferedWriter.close();
        }   

        // overrides existing file or create new file with contents from http body
        private void overrideFile(HttpExchange t, String filePath) throws IOException {
            // read request body
            InputStreamReader isr =  new InputStreamReader(t.getRequestBody(),"utf-8");
            BufferedReader br = new BufferedReader(isr);

            // request body to string
            int b;
            StringBuilder buf = new StringBuilder();
            while ((b = br.read()) != -1) {
                buf.append((char) b);
            }
            br.close();
            isr.close();
            
            // create and write to file
            FileWriter fileWriter = new FileWriter(filePath, false);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            bufferedWriter.write(buf.toString());
            bufferedWriter.close();
            
        }

        // delete file
        private void deleteFile(File file) throws IOException {
            file.delete();
        }

    }


}
