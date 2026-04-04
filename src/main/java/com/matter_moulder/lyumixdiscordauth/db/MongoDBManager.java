package com.matter_moulder.lyumixdiscordauth.db;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.set;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

public class MongoDBManager implements DatabaseManager {
    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> linkCollection;

    public MongoDBManager(String connectionString) {
        mongoClient = MongoClients.create(connectionString);
        database = mongoClient.getDatabase("minecraft");
        linkCollection = database.getCollection("link");
    }

    @Override
    public void savePlayerData(String name, String playersIp, String discordId) {
        Document player = new Document("name", name)
                .append("discord_id", discordId)
                .append("ip", playersIp)
                .append("last_login", 0);
        linkCollection.insertOne(player);

    }

    @Override
    public Object getPlayerIdByName(String name) {
        Document player = linkCollection.find(eq("name", name)).first();
        return player != null ? player.get("_id", ObjectId.class) : null;
    }

    @Override
    public Object getPlayerIdByDiscordId(String val) {
        Document player = linkCollection.find(eq("discord_id", val)).first();
        return player != null ? player.get("_id", ObjectId.class) : null;
    }

    @Override
    public String getPlayerName(Object id) {
        return (String) getPlayerField(id, "name");
    }
    
    @Override
    public Long getPlayerLastLoginTime(Object id) {
        return (Long) getPlayerField(id, "last_login");
    }

    @Override
    public String getPlayerDiscordId(Object id) {
        return (String) getPlayerField(id, "discord_id");
    }

    @Override
    public String getPlayerIp(Object id) {
        return (String) getPlayerField(id, "ip");
    }

    @Override
    public void setPlayerDiscordId(Object id, String value) {
        linkCollection.updateOne(eq("_id", id), set("discord_id", value));
    }

    @Override
    public void setPlayerIp(Object id, String value) {
        linkCollection.updateOne(eq("_id", id), set("ip", value));
    }

    @Override
    public void setPlayerLastLoginTime(Object id, Long value) {
        linkCollection.updateOne(eq("_id", id), set("last_login", value));
    }

    @Override
    public void deletePlayerData(Object id) {
        linkCollection.deleteOne(eq("_id", id));
    }

    @Override
    public void close() {
        mongoClient.close();
    }

    private Object getPlayerField(Object id, String field) {
        Document player = linkCollection.find(eq("_id", id)).first();
        return (player != null) ? player.get(field) : null;
    }
}
