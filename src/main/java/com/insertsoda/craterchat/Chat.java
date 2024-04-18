package com.insertsoda.craterchat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.insertsoda.craterchat.impl.CommandSourceImpl;
import com.insertsoda.craterchat.api.v1.CommandSource;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import finalforeach.cosmicreach.gamestates.InGame;
import finalforeach.cosmicreach.settings.Keybind;
import finalforeach.cosmicreach.ui.FontRenderer;
import finalforeach.cosmicreach.ui.UI;
import finalforeach.cosmicreach.ui.UITextInput;

import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class Chat {

    CommandDispatcher<CommandSource> commandDispatcher = new CommandDispatcher<>();
    ParseResults<CommandSource> parse;
    CompletableFuture<Suggestions> commandSuggestions = null;
    String infoString = "";
    LinkedList<ChatMessage> messages = new LinkedList<ChatMessage>();
    UITextInput textInput = null;
    boolean isOpen = false;
    float lineHeight = 0.0F;
    public static Keybind chatKeybind = new Keybind("chat", Input.Keys.T);

    public boolean isOpen() {
        return isOpen;
    }

    public void toggle(){
        this.isOpen = !this.isOpen;

        if(this.textInput != null) {
            if (this.isOpen) {
                this.textInput.onClick();

                if(UI.itemCatalog.shown){
                    UI.itemCatalog.hide();
                }
            } else {
                this.textInput.deactivate();
            }
        }
    }

    public void sendMessage(ChatMessage message){
        message.setStartTime(TimeUtils.millis());
        this.messages.addFirst(message);

        if(this.messages.size() > 15){
            this.messages.removeLast();
        }
    }

    public void sendMessage(String content){
        ChatMessage message = new ChatMessage(content);
        this.sendMessage(message);
    }

    public void clearChat() {
        this.messages = new LinkedList<>();
    }

    public CommandDispatcher<CommandSource> getCommandDispatcher(){
        return this.commandDispatcher;
    }

    public void render(Viewport uiViewport, OrthographicCamera uiCamera){
        if(this.lineHeight == 0.0F){
            this.lineHeight = FontRenderer.getTextDimensions(uiViewport, "Hello there", new Vector2()).y;
        }
        if(this.textInput == null){
            this.textInput = new UITextInput(0, 0, 0, 0) {
                @Override
                public void onCreate() {
                    super.onCreate();
                }

                @Override
                public boolean keyTyped(char character) {
                    // Types in the character into the text field and stores its returning boolean for later
                    boolean keyTypedBoolean = super.keyTyped(character);

                    // Handle suggestions and command syntax errors while typing
                    if(this.inputText.startsWith("/")){
                        parse = commandDispatcher.parse(this.inputText.substring(1), new CommandSourceImpl(InGame.getLocalPlayer(), InGame.world));

                        StringBuilder infoStringBuilder = new StringBuilder();

                        for(Map.Entry<CommandNode<CommandSource>, CommandSyntaxException> entry : parse.getExceptions().entrySet()){
                            infoStringBuilder.append(entry.getValue().getMessage()).append("\n");
                        }

                        commandSuggestions = commandDispatcher.getCompletionSuggestions(parse);

                        infoString = infoStringBuilder.toString();
                    }

                    // Handle sending messages/commands
                    if(Character.toString(character).equals("\n")){
                        if(this.inputText.startsWith("/")){
                            try {
                                commandDispatcher.execute(parse);
                            } catch (CommandSyntaxException e) {
                                CraterChat.Chat.sendMessage(new ChatMessage("[CommandSyntaxException]: " + e.getMessage()));
                            } catch (Exception e){
                                // This will only happen if a command itself causes an exception
                                CraterChat.Chat.sendMessage(new ChatMessage("[Unknown Exception]: " + e.getMessage()));
                            }
                        } else {
                            CraterChat.Chat.sendMessage(new ChatMessage("[Player]: " + this.inputText));
                        }

                        infoString = "";
                        commandSuggestions = null;
                        this.inputText = this.getDefaultInputText();
                        CraterChat.Chat.toggle();

                        return false;
                    }

                    // Resume other operations I guess
                    return keyTypedBoolean;
                }
            };
        }

        Gdx.gl.glActiveTexture(33984);
        UI.batch.setProjectionMatrix(uiCamera.combined);
        UI.batch.begin();

        float height = uiViewport.getWorldHeight() / 4.0F;

        for (ChatMessage message : this.messages) {
            height -= FontRenderer.getTextDimensions(uiViewport, message.getMessageContent(), new Vector2()).y;
            message.render(uiViewport, height);
        }

        if(this.isOpen){
            this.textInput.drawElementBackground(uiViewport, UI.batch);
            String text = this.textInput.inputText;

            if(this.textInput.inputText.startsWith("/")){
                if(commandSuggestions != null && commandSuggestions.isDone()){
                    try {
                        for (Suggestion suggestion : commandSuggestions.get().getList()) {
                            text += "\n" + suggestion.getText();
                        }
                    } catch(Exception ignored) {
                        // No crashes while suggesting thx
                    }
                }

                text += "\n" + infoString;
            }

            FontRenderer.drawTextbox(UI.batch, uiViewport, "> " + text, -uiViewport.getWorldWidth() / 2.0F + 25, uiViewport.getWorldHeight() / 4.0F + lineHeight * 0.5F, uiViewport.getWorldWidth() / 3.0F);

        }

        UI.batch.end();
    }
}
