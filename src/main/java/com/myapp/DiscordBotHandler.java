package com.myapp;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import java.util.Objects;

import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;


public class DiscordBotHandler extends ListenerAdapter {
    private static final String BOT_TOKEN = System.getenv("BOT_TOKEN");

    public static void main(String[] args) {
        JDA jda = JDABuilder.createDefault(BOT_TOKEN)
                .setRawEventsEnabled(true)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(new DiscordBotHandler())
                .setActivity(Activity.playing("ステータス"))
                .build();

        CommandListUpdateAction commands = jda.updateCommands();

        commands.addCommands(
                Commands.slash("minecraft", "Mange the Minecraft Server")
                        .setContexts(InteractionContextType.GUILD)
                        .setIntegrationTypes(IntegrationType.ALL)
                        .addOption(STRING, "action", "up or down", true)
        );

        commands.queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        // Only accept commands from guilds
        if (event.getGuild() == null)
            return;
        if (event.getName().equals("minecraft")) {
            minecraft(event, Objects.requireNonNull(event.getOption("action")).getAsString());
        } else {
            event.reply("I can't handle that command right now :(").setEphemeral(true).queue();
        }
    }

    private void minecraft(SlashCommandInteractionEvent event, String action) {
        try {
            if (action.matches("up")) {
                event.reply("Starting server...").queue();
                requestToServer("start");

            } else if (action.matches("down")) {
                event.reply("Stopping server...").queue();
                requestToServer("stop");
            }
        } catch (Exception e) {
            event.reply("An error occurred").setEphemeral(true).queue();
        }
    }

    private void requestToServer(String action) {
        // IAMユーザーの認証情報を設定
        AwsBasicCredentials awsCreds = AwsBasicCredentials.create(
                System.getenv("AWS_ACCESS_KEY_ID"),    // IAMユーザーのアクセスキーID
                System.getenv("AWS_SECRET_ACCESS_KEY") // IAMユーザーのシークレットキー
        );

        // Lambdaクライアントを作成
        LambdaClient lambdaClient = LambdaClient.builder()
                .region(Region.AP_NORTHEAST_1) // Lambdaのリージョン
                .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                .build();

        // Lambda関数を実行するためのペイロードを作成
        String payload = "{\"action\":\"" + action + "\"}";

        // Lambda関数のリクエストを構築
        InvokeRequest invokeRequest = InvokeRequest.builder()
                .functionName("EC2InstanceManager") // Lambda関数名
                .payload(SdkBytes.fromUtf8String(payload))
                .build();

        // Lambda関数を呼び出し
        InvokeResponse response = lambdaClient.invoke(invokeRequest);

        // レスポンスのステータスコードとペイロードを表示
        System.out.println("Status Code: " + response.statusCode());
        System.out.println("Response Payload: " + response.payload().asUtf8String());

        // Lambdaクライアントを閉じる
        lambdaClient.close();
    }
}
