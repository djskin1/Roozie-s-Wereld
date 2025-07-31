package RooziesPlugin.emotes;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class EmoteManager {
    private final File emoteFile;
    private final FileConfiguration emoteConfig;

    public EmoteManager(File dataFolder) {
        this.emoteFile = new File(dataFolder, "emotes.yml");

        if (!emoteFile.exists()) {
            dataFolder.mkdirs();
            try {
                emoteFile.createNewFile();
            } catch (IOException e) {
                Bukkit.getLogger().severe("Kon emotes.yml niet aanmaken!");
                e.printStackTrace();
            }
        }

        this.emoteConfig = YamlConfiguration.loadConfiguration(emoteFile);
    }

    public void performEmote(Player speler, String emoteName) {
        if (!emoteConfig.contains("emotes." + emoteName)) {
            speler.sendMessage("Deze emote bestaat niet.");
            return;
        }

        String message = emoteConfig.getString("emotes." + emoteName + ".message", "");
        if (!message.isEmpty()) {
            speler.sendMessage(message.replace("{player}", speler.getName()));
        }

        List<String> actions = emoteConfig.getStringList("emotes." + emoteName + ".actions");
        for (String action : actions) {
            performAction(speler, action);
        }
    }

    private void performAction(Player speler, String action) {
        switch (action.toLowerCase()) {
            case "swingarm":
            case "wave":
            case "kiss":
                speler.swingMainHand();
                break;
            case "sit":
                speler.sendMessage("Je gaat zitten.");
                break;
            case "cry":
                speler.sendMessage("Je huilt stilletjes... 😢");
                break;
            case "blush":
                speler.sendMessage("Je begint te blozen. 😊");
                break;
            case "hold_hand":
                speler.sendMessage("Je houdt iemands hand vast. 🤝");
                break;
            case "pain":
                speler.sendMessage("Je voelt pijn... 😣");
                break;
            case "angry":
                speler.sendMessage("Je bent boos! 😠");
                break;
            case "shocked":
                speler.sendMessage("Je bent geschrokken! 😲");
                break;
            case "shy":
                speler.sendMessage("Je voelt je verlegen. 😳");
                break;
            case "laugh":
                speler.sendMessage("Je lacht hardop! 😄");
                break;
            case "sleep":
                speler.sendMessage("Je valt in slaap... 😴");
                break;
            case "serious":
                speler.sendMessage("Je kijkt serieus. 😐");
                break;
            case "dream":
                speler.sendMessage("Je droomt... ☁️");
                break;
            case "kneel":
                speler.sendMessage("Je knielt neer.");
                break;
            case "hug":
                speler.sendMessage("Je geeft een knuffel.");
                break;
            case "push":
                speler.sendMessage("Je duwt iets weg.");
                break;
            case "grab":
                speler.sendMessage("Je pakt iets vast.");
                break;
            case "fall":
                speler.sendMessage("Je valt om.");
                break;
            case "scared":
                speler.sendMessage("Je bent bang. 😱");
                break;
            case "jealous":
                speler.sendMessage("Je kijkt jaloers. 😒");
                break;
            case "embarrassed":
                speler.sendMessage("Je bent beschaamd. 😳");
                break;
            case "proud":
                speler.sendMessage("Je kijkt trots. 😌");
                break;
            case "rest":
                speler.sendMessage("Je komt tot rust. 😌");
                break;
            case "lie":
                speler.sendMessage("Je ligt neer. 🛏️");
                break;
            default:
                speler.sendMessage("Onbekende actie: " + action);
                break;
        }
    }
}
