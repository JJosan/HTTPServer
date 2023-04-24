import java.io.*;
import java.net.*;
import java.util.*;

public class HTTPServer {
    private static final int PORT = 8080;
    private static final String FILE_ROOT = "resources";
    private static final HashMap<String, String> SUPPORTED_MIME_TYPES = new HashMap<>(){{
        put("txt", "text/plain");
        put("html", "text/html");
        put("json", "text/json");
    }};

    public static void main(String[] args) throws IOException {
        ServerSocket server = new ServerSocket(PORT);
        System.out.println("Server connect to port: " + PORT);
        while(true) {
            try {
                Socket socket = server.accept();
                InputStream in = socket.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                String firstLine = reader.readLine();

                String body = "";
                String requestMethod = "";
                String urlPath = "";
                String extension = "";

                if (firstLine != null) {
                    // get headers
                    requestMethod = firstLine.split(" ", 3)[0];
                    urlPath = firstLine.split(" ", 3)[1];

                    // check for valid mime
                    String[] temp = urlPath.split("\\.");
                    if (temp.length != 2) {
                        unsupportedMediaType(socket);
                        continue;
                    }
                    extension = temp[1];
                    if (!SUPPORTED_MIME_TYPES.containsKey(extension)) {
                        unsupportedMediaType(socket);
                        continue;
                    }

                    // get body
                    String line = "";
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("Content-Length")) break;
                    }
                    int cLength = Integer.valueOf(line.split(" ")[1]);
                    reader.readLine();
                    for (int i = 0, c = 0; i < cLength; i++) {
                        c = reader.read();
                        body += (char)c;
                    }
                }

                switch(requestMethod) {
                    case "GET":
                        handleGet(socket, FILE_ROOT+urlPath, extension);
                        break;
                    case "POST":
                        handlePost(socket, FILE_ROOT+urlPath, extension, body);
                        break;
                    case "PUT":
                        handlePut(socket, FILE_ROOT+urlPath, extension, body);
                        break;
                    case "DELETE":
                        handleDelete(socket, FILE_ROOT+urlPath);
                        break;
                    case "OPTIONS":
                        handleOptions(socket, extension);
                        break;
                    case "HEAD":
                        handleHead(socket, FILE_ROOT+urlPath, extension);
                        break;
                    default:
                        break;
                }
                reader.close();
            } catch(IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private static void handleGet(Socket socket, String filePath, String extension) throws IOException {
        // check if file exists
        if (!fileExists(filePath)) {
            notFound(socket);
            return;
        }

        // send contents
        send(socket, filePath, extension);
    }

    private static void handlePost(Socket socket, String filePath, String extension, String body) throws IOException {
        // check if file exists
        if (!fileExists(filePath)) {
            notFound(socket);
            return;
        }

        // check if mime is text/plain
        if (!extension.equals("txt")) {
            unsupportedMediaType(socket);
            return;
        }

        // get file
        FileWriter fileWriter = new FileWriter(filePath, true);
        BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);

        // write to file
        bufferedWriter.write(body);
        bufferedWriter.close();

        // send contents
        send(socket, filePath, extension);
    }

    private static void handlePut(Socket socket, String filePath, String extension, String body) throws IOException {
        // create and write to file
        FileWriter fileWriter = new FileWriter(filePath, false);
        BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
        bufferedWriter.write(body);
        bufferedWriter.close();

        // send contents
        send(socket, filePath, extension);
    }

    private static void handleDelete(Socket socket, String filePath) throws IOException {
        // check if file exists
        if (!fileExists(filePath)) {
            notFound(socket);
            return;
        }

        // delete file
        File file = new File(filePath);
        file.delete();

        // send status code
        OutputStream out = socket.getOutputStream();
        PrintWriter writer = new PrintWriter(out, true);
        String header = "HTTP/1.1 200 OK\r\n"+
                        "\r\n";
        writer.println(header);
        out.close();
        writer.close();
    }

    private static void handleOptions(Socket socket, String extension) throws IOException {
        // get options
        String body = "";
        switch(extension) {
            case "txt":
                body = "GET, POST, PUT, DELETE";
                break;
            case "html":
                body = "GET, PUT, DELETE";
                break;
            case "json":
                body = "GET, PUT, DELETE";
                break;
            default:
                body = "N/A";
                break;
        }

        // send options
        OutputStream out = socket.getOutputStream();
        PrintWriter writer = new PrintWriter(out, true);
        String header = "HTTP/1.1 200 OK\r\n"+
                        "Content-Type: text/plain\r\n"+
                        "Content-Length: " + body.length() + "\r\n"+
                        "\r\n";
        
        writer.println(header+body);
        out.close();
        writer.close();
    }

    private static void handleHead(Socket socket, String filePath, String extension) throws IOException {
        // check if file exists
        if (!fileExists(filePath)) {
            notFound(socket);
            return;
        }

        // send head only
        OutputStream out = socket.getOutputStream();
        PrintWriter writer = new PrintWriter(out, true);
        String body = readFile(filePath);
        String header = "HTTP/1.1 200 OK\r\n"+
                        "Content-Type: "+SUPPORTED_MIME_TYPES.get(extension)+"\r\n"+
                        "Content-Length: " + body.length() + "\r\n"+
                        "\r\n";
        writer.println(header);
        out.close();
        writer.close();
        
    }

    // returns contents from a filepath as a string
    private static String readFile(String filePath) throws IOException {
        File file = new File(filePath);
        BufferedReader br = new BufferedReader(new FileReader(file));
        String result = "";
        int c;
        while ((c = br.read()) != -1) {
            result += (char)c;
        }
        br.close();
        return result;
    }

    // sends status code 200 with contents of the file found at file path
    private static void send(Socket socket, String filePath, String extension) throws IOException {
        OutputStream out = socket.getOutputStream();
        PrintWriter writer = new PrintWriter(out, true);
        String body = readFile(filePath);
        String header = "HTTP/1.1 200 OK\r\n"+
                        "Content-Type: "+SUPPORTED_MIME_TYPES.get(extension)+"\r\n"+
                        "Content-Length: " + body.length() + "\r\n"+
                        "\r\n";
        writer.println(header + body);
        out.close();
        writer.close();
    }

    // sends status code 404
    private static void notFound(Socket socket) throws IOException {
        OutputStream out = socket.getOutputStream();
        PrintWriter writer = new PrintWriter(out, true);

        String body = "404 Not Found";

        String header = "HTTP/1.1 404 Not Found\r\n"+
                        "Content-Type: text/plain\r\n"+
                        "Content-Length: " + body.length() + "\r\n"+
                        "\r\n";
        writer.println(header + body);
        out.close();
        writer.close();
    }

    // sends status code 415
    private static void unsupportedMediaType(Socket socket) throws IOException {
        OutputStream out = socket.getOutputStream();
        PrintWriter writer = new PrintWriter(out, true);

        String body = "415 Unsupported Media Type";

        String header = "HTTP/1.1 415 Unsupported Media Type\r\n"+
                        "Content-Type: text/plain\r\n"+
                        "Content-Length: " + body.length() + "\r\n"+
                        "\r\n";
        writer.println(header + body);
        out.close();
        writer.close();
    }

    // returns true if file exists in the given file path
    private static boolean fileExists(String filePath) throws IOException {
        File file = new File(filePath);
        if (file.exists() && !file.isDirectory()) {
            return true;
        }
        return false;
    } 

}