package test.project1;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

import java.util.ArrayList;
import java.util.List;


public class Server extends AbstractVerticle {
    private Router router;
    private HttpServer server;
    private List<String> words = new ArrayList<>();

    @Override
    public void start(Promise<Void> start) throws Exception {
        router = Router.router(vertx);

        // enable parsing of request body
        router.route().handler(BodyHandler.create());

        // route to handle text analysis
        router.post("/analyze").handler(this::analyzeText);

        // serve static files
        router.route().handler(StaticHandler.create());

        // load the stored words
        FileSystem fileSystem = vertx.fileSystem();
        fileSystem.readFile("words.txt", result -> {
            if (result.succeeded()) {
                String fileContent = result.result().toString();
                String[] storedWords = fileContent.split("\\r?\\n");
                for (String word : storedWords) {
                    words.add(word);
                }
                System.out.println("Loaded " + words.size() + " words from file.");
            } else {
                System.out.println("Failed to load words from file: " + result.cause().getMessage());
            }

            // start the server
            vertx.createHttpServer().requestHandler(router)
                    .listen(config().getInteger("http.port", 8080))
                    .onSuccess(server -> {
                        this.server = server;
                        start.complete();
                    })
                    .onFailure(start::fail);
        });
    }

    private void analyzeText(RoutingContext context) {
        // get the text from the request body
        JsonObject requestBody = context.getBodyAsJson();
        String text = requestBody.getString("text");

        System.out.println(text);

        // if no words have been provided yet, return null for both response fields
        if (words.isEmpty()) {
            JsonObject response = new JsonObject();
            response.putNull("value");
            response.putNull("lexical");
            context.response()
                    .putHeader("content-type", "application/json")
                    .end(response.encode());
            return;
        }

        // analyze the text
        String closestValue = null;
        String closestLexical = null;
        int smallestValueDiff = Integer.MAX_VALUE;
        int smallestLexicalDiff = Integer.MAX_VALUE;
        for (String word : words) {
            int valueDiff = Math.abs(getValue(text) - getValue(word));
            int lexicalDiff = text.compareTo(word);
            if (valueDiff < smallestValueDiff) {
                closestValue = word;
                smallestValueDiff = valueDiff;
            }
            if (lexicalDiff > 0 && lexicalDiff < smallestLexicalDiff) {
                closestLexical = word;
                smallestLexicalDiff = lexicalDiff;
            }
        }

        // construct the response
        JsonObject response = new JsonObject();
        response.put("value", closestValue);
        response.put("lexical", closestLexical);

        // return the response
        context.response()
                .putHeader("content-type", "application/json")
                .end(response.encode());

        // add the new word to the list and store it in the file
        if (!words.contains(text)) {
            words.add(text);
            storeWords();
        }
    }

    private void storeWords() {
        vertx.fileSystem().writeFile("words.txt", Buffer.buffer(String.join("\n", words)), ar -> {
            if (ar.succeeded()) {
                System.out.println("Words stored successfully.");
            } else {
                System.err.println("Failed to store words: " + ar.cause().getMessage());
            }
        });
    }

    private int getValue(String word) {
        int value = 0;
        for (char c : word.toCharArray()) {
            value += c - 'a' + 1;
        }
        return value;
    }

    @Override
    public void stop(Promise<Void> stop) throws Exception {
        server.close(stop);
    }
}
