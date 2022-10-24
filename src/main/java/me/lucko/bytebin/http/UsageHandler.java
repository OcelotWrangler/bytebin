package me.lucko.bytebin.http;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.jooby.Context;
import io.jooby.Route;
import me.lucko.bytebin.content.ContentStorageHandler;
import me.lucko.bytebin.util.AuthorizationHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class UsageHandler implements Route.Handler {

    /** Logger instance */
    private static final Logger LOGGER = LogManager.getLogger(GetHandler.class);

    private final ContentStorageHandler storageHandler;
    private final AuthorizationHandler authorizationHandler;

    public UsageHandler(ContentStorageHandler storageHandler, AuthorizationHandler authorizationHandler) {
        this.storageHandler = storageHandler;
        this.authorizationHandler = authorizationHandler;
    }

    public void logUse(@Nonnull String userAgent, @Nonnull GetOrPost getOrPost, @Nullable String userId) {
        JsonObject json = this.storageHandler.loadMetrics();
        if (json == null) {
            json = new JsonObject();
        }
        JsonArray userAgentsArray = json.getAsJsonArray(getOrPost.value);
        if (userAgentsArray == null) {
            userAgentsArray = new JsonArray();
        }

        JsonObject userAgentJson = null;
        int toRemove = -1;
        for (int i = 0; i < userAgentsArray.size(); i++) {
            if (userAgentsArray.get(i).isJsonObject() && userAgentsArray.get(i).getAsJsonObject().has(userAgent)) {
                userAgentJson = userAgentsArray.get(i).getAsJsonObject();
                toRemove = i;
                break;
            }
        }

        if (toRemove != -1) {
            userAgentsArray.remove(toRemove);
        }

        JsonArray timeStampsArray;
        if (userAgentJson == null) {
            userAgentJson = new JsonObject();
            timeStampsArray = new JsonArray();
        } else {
            timeStampsArray = userAgentJson.getAsJsonArray(userAgent);
        }

        timeStampsArray.add(System.currentTimeMillis());
        userAgentJson.add(userAgent, timeStampsArray);
        userAgentsArray.add(userAgentJson);
        json.add(getOrPost.value, userAgentsArray);

        if (userId != null) {
            JsonObject uniqueUsersJson = json.getAsJsonObject("unique-users");
            if (uniqueUsersJson == null) {
                uniqueUsersJson = new JsonObject();
            }

            JsonArray getOrPostArray = uniqueUsersJson.getAsJsonArray(getOrPost.value);
            if (getOrPostArray == null) {
                getOrPostArray = new JsonArray();
            }

            toRemove = -1;
            for (int i = 0; i < getOrPostArray.size(); i++) {
                if (getOrPostArray.get(i).getAsString().contains(userId)) {
                    toRemove = i;
                    break;
                }
            }

            if (toRemove != -1) {
                getOrPostArray.remove(toRemove);
            }

            getOrPostArray.add(userId + "~" + System.currentTimeMillis());
            uniqueUsersJson.add(getOrPost.value, getOrPostArray);
            json.add("unique-users", uniqueUsersJson);
        }

        this.storageHandler.saveMetrics(json);
    }

    @Nonnull
    @Override
    public Object apply(@Nonnull Context ctx) {
        // check auth
        this.authorizationHandler.checkAuthorization(ctx);

        JsonObject metrics = this.storageHandler.loadMetrics();

        if (metrics == null) {
            return "{\"error\":\"not-found\"}";
        }

        JsonObject json = new JsonObject();

        JsonObject usageData = new JsonObject();
        usageData.add("get", getAllUserAgents(metrics.getAsJsonArray("get")));
        usageData.add("post", getAllUserAgents(metrics.getAsJsonArray("post")));
        json.add("usage-numbers-by-user-agent", usageData);

        JsonObject uniqueUserData = new JsonObject();
        uniqueUserData.add("get", getUniqueUserData(GetOrPost.GET));
        uniqueUserData.add("post", getUniqueUserData(GetOrPost.POST));
        json.add("number-of-unique-users", uniqueUserData);

        ctx.setResponseHeader("Content-Type", "application/json");

        return new Gson().toJson(json);
    }

    private JsonObject getUniqueUserData(GetOrPost getOrPost) {
        JsonObject json = this.storageHandler.loadMetrics();
        if (json == null) {
            json = new JsonObject();
        }

        JsonObject uniqueUsers = json.getAsJsonObject("unique-users");
        if (uniqueUsers == null) {
            uniqueUsers = new JsonObject();
        }

        return getUsageByTime(uniqueUsers.getAsJsonArray(getOrPost.value), true);
    }

    private JsonObject getAllUserAgents(JsonArray userAgents) {
        if (userAgents == null) {
            return new JsonObject();
        }
        JsonObject json = new JsonObject();
        for (JsonElement userAgent : userAgents) {
            for (String key : userAgent.getAsJsonObject().keySet()) {
                json.add(key, getUsageByTime(userAgent.getAsJsonObject().getAsJsonArray(key), false));
            }
        }
        return json;
    }

    private JsonObject getUsageByTime(JsonArray times, boolean splitForTime) {
        if (times == null) {
            return new JsonObject();
        }
        int total = 0;
        int lastDay = 0;
        int lastWeek = 0;
        int lastMonth = 0;

        for (JsonElement timeElement : times) {
            total += 1;
            Long time = splitForTime ? Long.parseLong(timeElement.getAsString().split("~")[1]) : timeElement.getAsLong();
            Long rightNow = System.currentTimeMillis();
            if (rightNow - time < 86400000) {
                lastDay += 1;
                lastWeek += 1;
                lastMonth += 1;
            } else if (rightNow - time < 604800000) {
                lastWeek += 1;
                lastMonth += 1;
            } else if (rightNow - time < 2629800000L) {
                lastMonth += 1;
            }
        }

        JsonObject data = new JsonObject();

        data.addProperty("total", total);
        data.addProperty("last-day", lastDay);
        data.addProperty("last-week", lastWeek);
        data.addProperty("last-month", lastMonth);

        return data;
    }

    enum GetOrPost {
        GET("get"),
        POST("post");

        public final String value;

        GetOrPost(String getOrPost) {
            this.value = getOrPost;
        }
    }
}
