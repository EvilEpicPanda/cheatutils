package com.zergatul.cheatutils.scripting.monaco;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.zergatul.cheatutils.scripting.CompilerFactory;
import com.zergatul.cheatutils.scripting.VisibilityCheck;
import com.zergatul.scripting.TextRange;
import com.zergatul.scripting.binding.Binder;
import com.zergatul.scripting.binding.BinderOutput;
import com.zergatul.scripting.binding.nodes.BoundNode;
import com.zergatul.scripting.compiler.CompilationParameters;
import com.zergatul.scripting.compiler.CompilationParametersBuilder;
import com.zergatul.scripting.lexer.Lexer;
import com.zergatul.scripting.lexer.LexerInput;
import com.zergatul.scripting.lexer.LexerOutput;
import com.zergatul.scripting.lexer.TokenType;
import com.zergatul.scripting.parser.NodeType;
import com.zergatul.scripting.parser.Parser;
import com.zergatul.scripting.parser.ParserOutput;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Integration {

    public void attach(HttpServer server, String prefix) {
        CompilationParametersResolver resolver = new CompilationParametersResolver() {
            @Override
            public CompilationParameters resolve(String type) {
                return CompilerFactory.createParameters(VisibilityCheck.getTypes(type));
            }
        };

        Theme theme = new DarkTheme();
        DocumentationProvider documentationProvider = new DocumentationProvider();
        HoverProvider hoverProvider = new HoverProvider(theme, documentationProvider);
        DefinitionProvider definitionProvider = new DefinitionProvider();
        CompletionProvider completionProvider = new CompletionProvider(documentationProvider);

        server.createContext(prefix, new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                try {
                    String path = exchange.getRequestURI().getPath();
                    if (path.equals(prefix + "tokenize")) {
                        Gson gson = new GsonBuilder().create();
                        byte[] data = exchange.getRequestBody().readAllBytes();
                        String request = new String(data, Charset.defaultCharset());
                        String code = gson.fromJson(request, String.class);

                        Lexer lexer = new Lexer(new LexerInput(code));
                        LexerOutput output = lexer.lex();
                        Json.sendResponse(exchange, output);
                    } else if (path.equals(prefix + "diagnostics")) {
                        Gson gson = new GsonBuilder().create();
                        byte[] data = exchange.getRequestBody().readAllBytes();
                        DiagnosticsRequest request = gson.fromJson(new String(data, Charset.defaultCharset()), DiagnosticsRequest.class);

                        LexerInput lexerInput = new LexerInput(request.code);
                        Lexer lexer = new Lexer(lexerInput);
                        LexerOutput lexerOutput = lexer.lex();

                        Parser parser = new Parser(lexerOutput);
                        ParserOutput parserOutput = parser.parse();

                        Binder binder = new Binder(parserOutput, resolver.resolve(request.type).getContext());
                        BinderOutput binderOutput = binder.bind();

                        Json.sendResponse(exchange, binderOutput.diagnostics().stream().map(d -> new DiagnosticsResponseItem(d.range, d.message)).toArray());
                    } else if (path.equals(prefix + "tokens")) {
                        Json.sendResponse(exchange, Arrays.stream(TokenType.values()).map(Enum::name).toArray());
                    } else if (path.equals(prefix + "nodes")) {
                        Json.sendResponse(exchange, Arrays.stream(NodeType.values()).map(Enum::name).toArray());
                    } else if (path.equals(prefix + "token-rules")) {
                        Json.sendResponse(exchange, Arrays.stream(TokenType.values()).map(type -> new TokenRule(type.name(), theme.getTokenColor(type))).toArray());
                    } else if (path.equals(prefix + "hover")) {
                        Gson gson = new GsonBuilder().create();
                        byte[] data = exchange.getRequestBody().readAllBytes();
                        HoverRequest request = gson.fromJson(new String(data, Charset.defaultCharset()), HoverRequest.class);

                        LexerInput lexerInput = new LexerInput(request.code);
                        Lexer lexer = new Lexer(lexerInput);
                        LexerOutput lexerOutput = lexer.lex();

                        Parser parser = new Parser(lexerOutput);
                        ParserOutput parserOutput = parser.parse();

                        Binder binder = new Binder(parserOutput, resolver.resolve(request.type).getContext());
                        BinderOutput binderOutput = binder.bind();

                        List<BoundNode> chain = new ArrayList<>();
                        findChain(chain, binderOutput.unit(), request.line, request.column);
                        Json.sendResponse(exchange, hoverProvider.get(chain));
                    } else if (path.equals(prefix + "definition")) {
                        Gson gson = new GsonBuilder().create();
                        byte[] data = exchange.getRequestBody().readAllBytes();
                        HoverRequest request = gson.fromJson(new String(data, Charset.defaultCharset()), HoverRequest.class);

                        LexerInput lexerInput = new LexerInput(request.code);
                        Lexer lexer = new Lexer(lexerInput);
                        LexerOutput lexerOutput = lexer.lex();

                        Parser parser = new Parser(lexerOutput);
                        ParserOutput parserOutput = parser.parse();

                        Binder binder = new Binder(parserOutput, resolver.resolve(request.type).getContext());
                        BinderOutput binderOutput = binder.bind();

                        BoundNode node = find(binderOutput.unit(), request.line, request.column);
                        Json.sendResponse(exchange, definitionProvider.get(node), TextRange.class);
                    } else if (path.equals(prefix + "completion")) {
                        Gson gson = new GsonBuilder().create();
                        byte[] data = exchange.getRequestBody().readAllBytes();
                        CompletionRequest request = gson.fromJson(new String(data, Charset.defaultCharset()), CompletionRequest.class);

                        LexerInput lexerInput = new LexerInput(request.code);
                        Lexer lexer = new Lexer(lexerInput);
                        LexerOutput lexerOutput = lexer.lex();

                        Parser parser = new Parser(lexerOutput);
                        ParserOutput parserOutput = parser.parse();

                        Binder binder = new Binder(parserOutput, resolver.resolve(request.type).getContext());
                        BinderOutput binderOutput = binder.bind();

                        Json.sendResponse(exchange, completionProvider.get(binderOutput, request.line, request.column));
                    } else {
                        exchange.sendResponseHeaders(404, 0);
                    }
                    exchange.close();
                } catch (Throwable e) {
                    e.printStackTrace();
                    exchange.sendResponseHeaders(503, 0);
                    exchange.close();
                }
            }
        });

        /*server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String path = exchange.getRequestURI().getPath();
                if (path.equals("/")) {
                    path = "/index.html";
                }

                Path filepath = Path.of(".\\src\\main\\resources\\web", path);
                if (Files.exists(filepath)) {
                    if (path.endsWith(".js")) {
                        exchange.getResponseHeaders().add("Content-Type", "text/javascript");
                    } else if (path.endsWith(".html")) {
                        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                    } else if (path.endsWith(".ttf")) {
                        exchange.getResponseHeaders().add("Content-Type", "font/ttf");
                    }

                    long size = Files.size(filepath);
                    exchange.sendResponseHeaders(200, size);
                    byte[] data = Files.readAllBytes(filepath);
                    exchange.getResponseBody().write(data);
                } else {
                    exchange.sendResponseHeaders(404, 0);
                }

                exchange.close();
            }
        });*/
    }

    private static BoundNode find(BoundNode node, int line, int column) {
        if (node.getRange().contains(line, column)) {
            for (BoundNode child : node.getChildren()) {
                if (child.getRange().contains(line, column)) {
                    return find(child, line, column);
                }
            }
            return node;
        } else {
            return null;
        }
    }

    private static void findChain(List<BoundNode> chain, BoundNode node, int line, int column) {
        if (node.getRange().contains(line, column)) {
            for (BoundNode child : node.getChildren()) {
                if (child.getRange().contains(line, column)) {
                    findChain(chain, child, line, column);
                }
            }
            chain.add(node);
        }
    }

    public record TokenRule(String token, String foreground) {}

    public record DiagnosticsRequest(String code, String type) {}

    public record DiagnosticsResponseItem(TextRange range, String message) {}

    public record HoverRequest(String code, String type, int line, int column) {}

    public record CompletionRequest(String code, String type, int line, int column) {}
}