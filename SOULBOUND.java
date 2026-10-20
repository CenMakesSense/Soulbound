/*
 * SOULBOUND - an ASCII terminal RPG (plain Java, standard input/output only).
 * Works in Termux and any normal terminal.   Compile: javac SOULBOUND.java    Run: java SOULBOUND
 *
 * Sections: 1 Data | 2 State | 3 ASCII art | 4 Rendering | 5 Input | 6 Title/main | 7 Story flow
 *           8 Exploration | 9 Battle | 10 Endings
 */
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;

public class SOULBOUND {

    // ================= 1. DATA =================
    static final int MAX_HP = 30, REST_HEAL = 12, WIN_HEAL = 4, SHARD_TOTAL = 11;
    static final int HOLLOW = 0, SHARDLING = 1, WARDEN = 2, REMNANT = 3;            // enemy types
    static final int HEAVY = 0, SWEEP = 1, CHARGE = 2;                              // attack kinds
    static final int IDLE = 0, PREPARE = 1, ATTACK = 2, RECOVER = 3, STAGGER = 4, DEFEATED = 5; // enemy poses
    static final int CONTINUE = 0, WON = 1, SPARED = 2, LOST = 3;                   // battle results
    static final int CANVAS_W = 36, CANVAS_H = 10, ENEMY_COL = 17;
    static final String RESET = "\033[0m", CYAN = "\033[96m", VIOLET = "\033[95m", RED = "\033[91m",
            GREY = "\033[90m", WHITE = "\033[97m";

    static final String[] LOCATION_NAMES = {"Forgotten Passage", "Silent District", "Warden's Chamber", "Memory Gate"};
    static final String[] ENDING_NAMES = {"RESTORATION", "SEVERANCE", "FORGOTTEN", "THE WHOLE"};
    static final String[] OFFENSE_NAMES = {"slash", "pierce", "slam"};

    static final String[][] LOOKS = {
        {"Black water drips from a cracked ceiling. Pale roots cling to the walls, and far ahead a thin light breathes in and out like a sleeping lung.",
         "Carved stones lie scattered in the dust. Each bears a symbol you almost recognise: a lantern with an open hand beneath it.",
         "Your footsteps make no echo here. Somewhere in the dark, something drags itself along the stone."},
        {"Hollow towers lean over a silent street. Shutters hang open on rooms still set for dinner, the plates untouched for centuries.",
         "A cracked clock above the square has stopped at the same hour as every other clock in the district.",
         "Chalk marks cover the road: a map of the district with one building circled again and again. The Archive."},
        {"The chamber is vast. Chains hang from the ceiling like iron rain, and a circle of violet light pulses around the colossal Warden.",
         "Hundreds of tiny names are carved into the pillars, each one scratched out by a stone finger.",
         "The Warden does not move, but the crystal in its chest brightens when you step closer."},
        {"A ring of pale fire floats above a pool of still silver. Faces rise and sink within it: thousands of memories waiting to be called.",
         "Three pillars stand around the Gate, each humming a different note. Your chest hums with them.",
         "The air tastes of rain and old lamplight. Everything here feels like a held breath."}};

    // Memory Shards 0-10. Hidden (optional) shards are 1, 3 and 5.
    static final String[] SHARD_NAMES = {"A Voice in Glass", "Hollow's Whisper", "Research Log: Binding", "Archive: Subject Seven",
        "Rootbound Lullaby", "Wall Inscription", "The Warden's Oath", "Pillar of Origin", "Pillar of Collapse", "Pillar of Mirror",
        "The Unsent Letter"};
    static final String[] SHARD_TEXTS = {
        "A woman's voice, warm and tired: 'Day 212. The Lantern Project holds. We are teaching memories to outlive the people who made them. If it works, no one will ever be forgotten.' The shard grows warm in your chest.",
        "A broken whisper not meant for you: 'Do not let me forget the garden... do not let me become the mask.' It sounds like something that once wanted to stay a person.",
        "Archivist Lanth: 'Memory alone fades, so we bind each memory to a soul-imprint, a trace of the person's spirit, and store it in the Gate. But the imprints resist. Some memories are... leaving their owners.'",
        "'Subject Seven is the only imprint that took the binding completely. Its memories do not drift. It believes it is still the apprentice. We should tell it. We will not tell it.' The rest of the page is torn away.",
        "A lullaby hummed by dozens of overlapping voices. They are not singing to you. They are singing to each other to keep from coming apart.",
        "Carved into the stone: 'Guard the Gate. Let no unbound memory pass.' Below it, scratched later: 'The Gate decides who is a memory and who is a person. Forgive me. I never could.'",
        "The Warden's crystal speaks: 'We built the Lantern to keep memories safe. It failed. Memories tore from their owners, gathered, and woke. We named them Echoes. I was set to keep them from the Gate. But you walked in and the Gate sang. Imprint... you are part of this. Go to the Gate.'",
        "A young apprentice copies a dying master's memories into a crystal. The apprentice smiles, then laughs, then cries. The master whispers, 'Remember me.' You know this face. It is the face behind your mask.",
        "The Gate shudders. Memories tear loose from their imprints and pour across the city like light through water. Most shatter into Hollows and Shardlings. One imprint holds, cradling stolen memories until they begin to sound like a voice. The voice says: 'I am here.'",
        "A mirror forms. The mask is yours. The glow in your chest is the imprint. You were not born; you coalesced. The apprentice never lived past that day. You are what their memory became, and you chose to keep going.",
        "A letter from the apprentice to the future: 'If you are reading this, you are not me. Good. Do not carry my life. Carry what I hoped to protect: that no memory should be lost and no one forced to serve it. Teach them to remember without the Gate.'"};

    // ================= 2. STATE =================
    static Scanner in = new Scanner(System.in);
    static Random rng = new Random();
    static boolean useColor, useEffects;       // colors / typing + short animations (can be switched off)
    static int width = 44;                     // total panel width, adapts to the terminal
    static int loc, hp, resonance, ending, curTag, curType, enemyAnim, playerPose;
    static boolean[] found = new boolean[SHARD_TOTAL];
    static boolean[] restUsed = new boolean[4];
    static int[] lookCount = new int[4];
    static Set<String> done = new HashSet<>();   // finished interactions, e.g. "hollow", "pillar2"
    static boolean offBalance, opening, leave, quitToTitle, typingNow;
    static Enemy enemy;
    static String note = "";
    static String lastPrompt = "";                 // remembered so automated tests can answer the right menu
    static List<String> lastOptions = new ArrayList<>();

    static class Enemy {
        String name;
        int type, hp, maxHp, posture, maxPosture, damage, turn, attack;
        int[] pattern;
        boolean mercy, staggered, defensivePose;

        Enemy(int type) {
            this.type = type;
            if (type == HOLLOW) { name = "Hollow"; maxHp = 14; maxPosture = 8; damage = 4; mercy = true; pattern = new int[]{HEAVY, HEAVY, SWEEP, HEAVY, CHARGE}; }
            if (type == SHARDLING) { name = "Shardling"; maxHp = 18; maxPosture = 10; damage = 4; mercy = true; pattern = new int[]{SWEEP, CHARGE, SWEEP, CHARGE}; }
            if (type == WARDEN) { name = "Warden"; maxHp = 32; maxPosture = 12; damage = 8; mercy = false; pattern = new int[]{HEAVY, SWEEP, HEAVY, CHARGE}; }
            if (type == REMNANT) { name = "Remnant"; maxHp = 26; maxPosture = 12; damage = 6; mercy = true; pattern = new int[]{SWEEP, CHARGE, HEAVY, HEAVY, SWEEP}; }
            hp = maxHp;
            posture = maxPosture;
        }
    }

    // ================= 3. ASCII ART =================
    static final String[] WORDMARK_SOUL = {
        "  ____   ___  _   _ _",
        " / ___| / _ \\| | | | |",
        " \\___ \\| | | | | | | |",
        "  ___) | |_| | |_| | |___",
        " |____/ \\___/ \\___/|_____|"};
    static final String[] WORDMARK_BOUND = {
        "  ____   ___  _   _ _   _ ____",
        " | __ ) / _ \\| | | | \\ | |  _ \\",
        " |  _ \\| | | | | | |  \\| | | | |",
        " | |_) | |_| | |_| | |\\  | |_| |",
        " |____/ \\___/ \\___/|_| \\_|____/"};

    // Location backgrounds (bottom-aligned on the canvas). '*' glows cyan, '+' violet.
    static final String[][] BACKGROUND = {
        {"/\\_/\\__/\\___/\\_/\\___/\\__/\\_/\\___/\\_",
         "|  |   .      |  |    '  *   |  |   .",
         "|  |  ,     / |  |  .       /|  |    ",
         "|  |      /\\ _|  |     ,   / |  |  ' ",
         "|  |  *     \\   |  |       '  |  |   ",
         "|__|    .    ,  |__|   .      |__|  ,",
         "  ,   _   .   ____    ,   __    .   ,",
         " . ,_|_|_. , |____| . ,_._|_|_ . , . ",
         "~~ ~~~ ~~  ~~~ ~~ ~~~  ~~ ~~~ ~~ ~~~ "},
        {"      _            ___          _     ",
         "   __| |__      _.'   '._    __| |__  ",
         "  |[]  [] |    | [] [] x |  |[]  [] | ",
         "  |  __   |    |  __  __ |  | x __   | ",
         "  |_|  |__|    |_|  ||  _|  |_|  |__| ",
         "   ..:..  *      ..:..       ..:..    ",
         " _ ,. _  .  ,_  .  _,  . _  ,.  _  . ",
         "~~ ~~ ~~ ~~~~ ~~ ~~ ~~~~ ~~ ~~ ~~~ ~~ "},
        {"====================================",
         "||  ||  o        ||        o  ||  ||",
         "||  ||  |   *    ||    *   |  ||  ||",
         "||  ||  o    .-======-.    o  ||  ||",
         "||  ||  |    |  [  ] |    |  ||  ||",
         "||  ||  o    |  [  ] |    o  ||  ||",
         "||__||  |    |_[____]_|    |  ||__||",
         "  ..  ,  .  ,   ..    ,  .   ,   .. ",
         "~~~ ~~~ ~~ ~~~ ~~~ ~~ ~~~ ~~~ ~~ ~~~"},
        {mirror("        .-======="), mirror("      .'  *   .   "), mirror("     /  .-'''''''"),
         mirror("    |  /  *   .  +"), mirror("    |  | <  <  <  "), mirror("    |  \\  *   .  +"),
         mirror("     \\  '-........"), mirror("   *  '.      .   *"), mirror("  ~  ^  '-=========")}};

    static final String[] FOREGROUND = {"_.,._,  .,_ ,.__,  ._,.  ,_.,_.,.", "..:.. ,. ..:. .,.. :.. ,.:.. ,. ..:", "..  ,  ..  ,  ..  ,  ..  ,  ..  , ", "* . * . * . * . * . * . * . * . * ."};

    static final String[] PLAYER = {" .-. ", " (_) ", "/|*|\\", "| | |", "|/ \\|", "'^^^'"};

    static final String[][] ENEMY_ART = {
        {"    .-\"\"-.", "   / o  o \\", "   | \\__/ |", "  _\\ '==' /_", " / |  +   | \\", "/  |      |  \\", "|_/ \\____/ \\_|", "   /|    |\\"},
        {"  /\\        /\\", " /  \\  +   /  \\", "/_*__\\    /__*_\\", "    \\ \\  / /", "  +  \\ (  ) /  +", "   /\\ \\\\\\//  /\\", "  /  \\ \\||/ /  \\", "  \\__/  '**'  \\__/"},
        {"     .----.", "     |====|", " .--'-.**.-'--.", "o=| /|_**_|\\ |=o", " =|_/ |__| \\_|=", "  |  _|  |_  |", "  |_| |  | |_|", " _||__|  |__||_"},
        {"  \\ | /  \\|/ /", " --\\\\ \\|// //--", "  _//\\\\(*)//\\\\_", " / / /(*+*)\\ \\ \\", "{  | /\\*/\\ |  }", " \\_|/  \\_/  \\|_/", "   \\\\/ || \\//", "  _/ \\ || / \\_"}};
    static final String[] SHARDLING_SHELL = {"    .-/\\-\\-.", "  .'/ /\\ \\ \\'.", " / | |  * | | \\", " | | | () | | |", " \\ | |  *  | | /", "  '.\\ \\/ / /.'", "    '-\\/-/-'"};

    static final String[][] ENDING_ART = {
        {"        \\   |   /", "     .   \\  |  /   .", "   ----   .-===-.   ----", "         /  * *  \\", "   o    |  o * o  |    o", "  /|\\    \\   _   /    /|\\", "  / \\     '-----'     / \\"},
        {"       .-===/\\===-.", "     .'   /  \\   '.", "    /    /    \\    \\", "   |    / !! !! \\   |", "    \\    \\    /    /", "     '.   \\  /   .'", "       '-==\\/==-'"},
        {"  . . .  ~ ~ ~  . . .  ~ ~", " ~ ~   . . .   ~ ~ ~   . . ", "   .  ~  .  ~  . .  ~  . ", "        .-.", "        (_)", "       /| |\\   ... fading", " . . ~ ~ . . ~ ~ . . ~ ~ ."},
        {"     * .  .-=====-.  . *", "   .   .'  * * *  '.   .", "  *   /  .-=====-.  \\   *", "  o  |  /  * * *  \\  |  o", " /|\\ | |  *  @  *  | | /|\\", " / \\ |  \\  * * *  /  | / \\", "      \\  '-=====-'  /", "   *   '.    *    .'   *"}};

    static String mirror(String half) {                       // builds a symmetrical row from its left half
        StringBuilder sb = new StringBuilder(half);
        while (sb.length() < 18) sb.append(' ');
        StringBuilder right = new StringBuilder(sb).reverse();
        String r = right.toString();
        StringBuilder out = new StringBuilder(sb);
        for (char c : r.toCharArray()) {
            if (c == '/') out.append('\\'); else if (c == '\\') out.append('/');
            else if (c == '<') out.append('>'); else if (c == '>') out.append('<');
            else if (c == '(') out.append(')'); else if (c == ')') out.append('(');
            else out.append(c);
        }
        return out.toString();
    }

    // ================= 4. RENDERING =================
    static void detectTerminal() {
        String cols = System.getenv("COLUMNS");
        if (cols != null) {
            try { width = Math.max(40, Math.min(50, Integer.parseInt(cols.trim()) - 1)); } catch (NumberFormatException e) { /* keep default */ }
        }
        boolean interactive = System.console() != null;       // false when input/output is piped
        boolean plain = System.getenv("NO_COLOR") != null || "dumb".equals(System.getenv("TERM"));
        useColor = interactive && !plain;
        useEffects = interactive;
    }

    static int inner() { return width - 4; }

    static void println(String s) { System.out.println(s); }

    static void sleep(int ms) {
        if (!useEffects) return;
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    static void clearScreen() {
        if (useColor) { System.out.print("\033[2J\033[H"); System.out.flush(); }
        else println("\n.  .  .  .  .  .  .  .  .  .  .  .\n");   // fallback: short separator, no wall of blank lines
    }

    static void border() { println("+" + "-".repeat(width - 2) + "+"); }

    static String paint(String s, String col) { return (useColor && !col.isEmpty()) ? col + s + RESET : s; }

    // Art lines: '*' glows cyan, '+' is violet, '!' is red, everything else uses the base color.
    static String paintArt(String s, String base) {
        if (!useColor) return s;
        StringBuilder sb = new StringBuilder(base);
        for (char c : s.toCharArray()) {
            if (c == '*') sb.append(CYAN).append(c).append(RESET).append(base);
            else if (c == '+') sb.append(VIOLET).append(c).append(RESET).append(base);
            else if (c == '!') sb.append(RED).append(c).append(RESET).append(base);
            else sb.append(c);
        }
        return sb.append(RESET).toString();
    }

    // Prints one panel row, never wider than the panel. Optionally types it out character by character.
    static void line(String raw, String col, boolean art) {
        if (raw.length() > inner()) raw = raw.substring(0, inner());
        String pad = " ".repeat(inner() - raw.length());
        System.out.print("| ");
        if (typingNow && useEffects) {
            if (useColor && !col.isEmpty()) System.out.print(col);
            for (char c : raw.toCharArray()) { System.out.print(c); System.out.flush(); sleep(6); }
            if (useColor && !col.isEmpty()) System.out.print(RESET);
        } else System.out.print(art ? paintArt(raw, col) : paint(raw, col));
        println(pad + " |");
    }

    static void center(String s, String col) {
        if (s.length() > inner()) s = s.substring(0, inner());
        line(" ".repeat((inner() - s.length()) / 2) + s, col, false);
    }

    static List<String> wrap(String text, int w) {
        List<String> out = new ArrayList<>();
        String cur = "";
        for (String word : text.split(" ")) {
            if (word.length() > w) word = word.substring(0, w);
            if (cur.isEmpty()) cur = word;
            else if (cur.length() + 1 + word.length() <= w) cur += " " + word;
            else { out.add(cur); cur = word; }
        }
        out.add(cur);
        return out;
    }

    // Dialogue/narration block. Paragraph prefixes: "<*>" = discovery (cyan, centered), "!!" = warning (red).
    static void panel(String text) {
        typingNow = text.length() < 700;
        for (String para : text.split("\n", -1)) {
            if (para.startsWith("<*>")) center(para.substring(3).trim(), CYAN);
            else if (para.startsWith("!!")) for (String s : wrap(para.substring(2).trim(), inner())) line(s, RED, false);
            else for (String s : wrap(para, inner())) line(s, "", false);
        }
        typingNow = false;
    }

    static void banner(String top, String bottom, String col) {
        border();
        center(top, CYAN);
        if (!bottom.isEmpty()) center(bottom, col);
        border();
    }

    static String bar(int value, int max, int len) {
        int filled = max <= 0 ? 0 : (Math.max(0, value) * len + max - 1) / max;
        return "[" + "#".repeat(filled) + ".".repeat(len - filled) + "]";
    }

    static void statusLine() {
        line("HP " + bar(hp, MAX_HP, 8) + " " + hp + "/" + MAX_HP + "  RES " + resonance + "  SHARDS " + shardCount() + "/" + SHARD_TOTAL, WHITE, false);
    }

    // ----- canvas: layers are stamped on top of each other (background, environment, character, foreground) -----
    static char[][] newCanvas() {
        char[][] c = new char[CANVAS_H][CANVAS_W];
        for (char[] row : c) Arrays.fill(row, ' ');
        return c;
    }

    static void stamp(char[][] c, String[] sprite, int row, int col, boolean opaque) {
        for (int r = 0; r < sprite.length; r++)
            for (int k = 0; k < sprite[r].length(); k++) {
                int y = row + r, x = col + k;
                if (y < 0 || y >= CANVAS_H || x < 0 || x >= CANVAS_W) continue;
                if (sprite[r].charAt(k) != ' ' || opaque) c[y][x] = sprite[r].charAt(k);
            }
    }

    static void printCanvas(char[][] c, String col) {
        int shown = Math.min(CANVAS_W, inner());
        String margin = " ".repeat(Math.max(0, (inner() - shown) / 2));     // keep the picture centered
        for (char[] row : c) line(margin + new String(row, 0, shown), col, true);
    }

    static String[] playerSprite() {
        String[] s = PLAYER.clone();
        if (glowStage() >= 3) s[0] = "*.-.*";                       // the glow spreads as the truth nears
        if (playerPose == 1) s[2] += "))";                          // parry
        if (playerPose == 3) s[2] += "==>";                         // interrupt
        return s;
    }

    static int glowStage() { return ending >= 0 ? 4 : loc; }

    static void drawLocation(String msg) {
        clearScreen();
        banner("S O U L B O U N D", LOCATION_NAMES[loc].toUpperCase(), WHITE);
        char[][] c = newCanvas();
        stamp(c, BACKGROUND[loc], CANVAS_H - BACKGROUND[loc].length, 0, false);     // layer 1: environment
        stamp(c, playerSprite(), CANVAS_H - PLAYER.length - 1, 4, true);            // layer 2: character
        stamp(c, new String[]{FOREGROUND[loc]}, CANVAS_H - 1, 0, false);            // layer 3: foreground
        printCanvas(c, GREY);
        border();
        statusLine();
        border();
        panel(msg);
        border();
    }

    // ================= 5. INPUT =================
    static String readLine() {
        if (!in.hasNextLine()) {                                   // end of input: leave cleanly
            println("\n[Input ended] The light fades. Goodbye.");
            System.exit(0);
        }
        return in.nextLine().trim();
    }

    static void pause() {
        lastPrompt = "[Enter]";
        System.out.print("   [Enter] ");
        System.out.flush();
        readLine();
    }

    static int choose(String prompt, String... options) { return chooseList(prompt, Arrays.asList(options)); }

    static int chooseList(String prompt, List<String> options) {
        lastPrompt = prompt;
        lastOptions = options;
        line(prompt, WHITE, false);
        for (int i = 0; i < options.size(); i++) line("[" + (i + 1) + "] " + options.get(i), CYAN, false);
        border();
        while (true) {                                             // repeats until the answer is valid
            System.out.print("> ");
            System.out.flush();
            String s = readLine();
            try {
                int n = Integer.parseInt(s);
                if (n >= 1 && n <= options.size()) return n - 1;
            } catch (NumberFormatException e) { /* fall through to the message */ }
            println("  Please type a number from 1 to " + options.size() + ".");
        }
    }

    // ================= 6. TITLE, SETTINGS, MAIN =================
    static void printTitleScreen() {
        clearScreen();
        border();
        line("", "", false);
        for (String s : WORDMARK_SOUL) line(" ".repeat((inner() - 28) / 2) + s, CYAN, false);
        for (String s : WORDMARK_BOUND) line(" ".repeat((inner() - 32) / 2) + s, CYAN, false);
        line("", "", false);
        center("~  What remembers you, when you do not?  ~", VIOLET);
        line("", "", false);
        char[][] c = newCanvas();
        stamp(c, new String[]{" *   .      +     .   *      +  ", "    .   *      .      *   .     "}, 0, 0, false);
        stamp(c, PLAYER, 3, 15, true);
        printCanvas(c, GREY);
        border();
    }

    static void settingsMenu() {
        while (true) {
            clearScreen();
            banner("SETTINGS", "", WHITE);
            int c = choose("Colors: " + (useColor ? "ON" : "OFF") + "   Effects: " + (useEffects ? "ON" : "OFF"),
                "Toggle colors", "Toggle typing and animation", "Back");
            if (c == 0) useColor = !useColor;
            else if (c == 1) useEffects = !useEffects;
            else return;
        }
    }

    public static void main(String[] args) {
        detectTerminal();
        boolean running = true;
        while (running) {
            printTitleScreen();
            int c = choose("Choose", "New Game", "Settings", "Quit");
            if (c == 0) playGame();
            else if (c == 1) settingsMenu();
            else running = false;
        }
        println("\nThe light dims. Farewell, Unremembered.");
    }

    // ================= 7. GAME FLOW =================
    static void resetState() {
        hp = MAX_HP; resonance = 0; loc = 0; ending = -1; enemyAnim = IDLE; playerPose = 0; curTag = 0; curType = 0;
        Arrays.fill(found, false);
        Arrays.fill(restUsed, false);
        Arrays.fill(lookCount, 0);
        done.clear();
        enemy = null; offBalance = false; opening = false; leave = false; quitToTitle = false; note = "";
    }

    static void playGame() {
        resetState();
        introScene();
        for (int l = 0; l < 4 && !quitToTitle; l++) {
            loc = l;
            transition(LOCATION_NAMES[l]);
            String first = arrive(l);
            leave = false;
            exploreLoop(first);
        }
        if (!quitToTitle && ending >= 0) showEnding(ending);
    }

    static void transition(String name) {
        clearScreen();
        border();
        center("...", GREY);
        for (int i = 0; i < 3; i++) { sleep(180); center(". . . . . . . . . .".substring(0, 6 + i * 6), GREY); }
        center(name.toUpperCase(), CYAN);
        border();
        sleep(250);
    }

    static void introScene() {
        clearScreen();
        border();
        char[][] c = newCanvas();
        stamp(c, new String[]{" *      .        +       .     *", "     .      *      .     +        ", "  +       .     *       .      *"}, 1, 0, false);
        stamp(c, new String[]{" .-. ", " (_) ", "/|*|\\"}, 6, 15, true);
        stamp(c, new String[]{"~ . ~ _ . ~ _ ~ . _ ~ _ . ~ _ . ~ _ "}, 9, 0, false);
        printCanvas(c, GREY);
        border();
        panel("You do not remember waking.");
        border();
        pause();
    }

    static String arrive(int l) {
        if (l == 0) {
            storyPage("Cold. Black water against your cheek. You wake on broken stone, and nothing comes to you: no name, no face, no past. "
                + "You are certain of one thing only: you must be human, because what else would wake up afraid?");
            storyPage("A pool of still water lies beside you. You lean over it. The face looking back is pale and perfectly smooth, like a mask, "
                + "and in your chest a small cyan light pulses in time with your heartbeat. Perhaps it is only the water playing tricks.");
            return "HOW TO PLAY: choose a numbered action. A star (*) marks what you have already done. Careless searching can wake things in the dark.\n\n"
                + "In battle, read the enemy's warning, then defend: PARRY heavy strikes, DODGE wide sweeps, INTERRUPT charges. Then answer with Slash, Pierce or Slam.";
        } else if (l == 1) {
            storyPage("The passage opens onto a city of grey towers, empty and silent. Whoever lived here left in a hurry, or never left at all. "
                + "Your chest light brightens, as if it knows this place.");
            return look();
        } else if (l == 2) {
            storyPage("A vast chamber. Chains the width of your body descend into the dark, and at the centre kneels a towering guardian of cracked stone, "
                + "its helmet sealed, a crystal burning in its chest.");
            storyPage("The crystal flares. A voice grinds out of the stone: 'Imprint... You carry the light of Seven. I remember that glow. "
                + "You should not be able to walk. You should not have come.' The chains begin to rattle.");
            return "The Warden has not yet moved. Whatever it recognises in you, it does not forgive.";
        }
        storyPage("Beyond the chamber the stairs descend into silver light. The Memory Gate: a ring of pale fire, the heart of the failed Lantern Project. "
            + "Everything in you wants to run toward it, and away from it.");
        return look();
    }

    static void storyPage(String text) {
        drawLocation(text);
        pause();
    }

    static String look() {
        String text = LOOKS[loc][lookCount[loc] % 3];
        lookCount[loc]++;
        return text;
    }

    static boolean collect(int i) {
        if (found[i]) return false;
        found[i] = true;
        return true;
    }

    static String shardText(int i) {
        return "<*>[ MEMORY SHARD: " + SHARD_NAMES[i] + " ]\n" + SHARD_TEXTS[i];
    }

    static int shardCount() {
        int n = 0;
        for (boolean b : found) if (b) n++;
        return n;
    }

    static int hiddenCount() { return (found[1] ? 1 : 0) + (found[3] ? 1 : 0) + (found[5] ? 1 : 0); }

    // ================= 8. EXPLORATION =================
    static void exploreLoop(String msg) {
        while (!leave && !quitToTitle) {
            drawLocation(msg);
            String[] labels = exploreLabels();
            List<String> shown = new ArrayList<>();
            List<Integer> ids = new ArrayList<>();
            for (int i = 0; i < labels.length; i++) if (labels[i] != null) { shown.add(labels[i]); ids.add(i); }
            shown.add("Quit to title");
            ids.add(10);
            msg = handleExplore(ids.get(chooseList("What will you do?", shown)));
        }
    }

    static String tag(String label, boolean isDone) { return isDone ? label + " *" : label; }

    static String[] exploreLabels() {
        String[] L = new String[10];
        L[0] = "Look around";
        if (loc == 0) {
            L[1] = tag("Examine crystal", found[0]);
            L[2] = tag("Investigate sound", done.contains("hollow"));
            L[3] = tag("Search alcove", done.contains("alcove"));
            L[4] = "Go onward";
        } else if (loc == 1) {
            L[1] = tag("Read terminal", found[2]);
            L[2] = tag("Enter clock tower", done.contains("shardling"));
            L[3] = tag("Search archive", done.contains("archive"));
            L[4] = tag("Peer into well", done.contains("remnant"));
            L[5] = "Go onward";
        } else if (loc == 2) {
            L[1] = tag("Read inscription", found[5]);
            L[2] = tag("Speak to Warden", done.contains("speak") || done.contains("warden"));
            L[3] = tag("Challenge Warden", done.contains("warden"));
            L[4] = "Go onward";
        } else {
            L[1] = tag("Touch pillar I", done.contains("pillar1"));
            L[2] = tag("Touch pillar II", done.contains("pillar2"));
            L[3] = tag("Touch pillar III", done.contains("pillar3"));
            L[4] = tag("Listen to hum", found[10]);
            L[5] = "Approach the Gate";
        }
        L[7] = "Memories";
        L[8] = "Objectives";
        L[9] = "Rest / Status";
        return L;
    }

    static String handleExplore(int i) {
        if (i == 10) { quitToTitle = true; return ""; }
        if (i == 7) return showMemories();
        if (i == 8) return showObjectives();
        if (i == 9) return restText();
        if (i == 0) return look();
        if (loc == 0) return actPassage(i);
        if (loc == 1) return actDistrict(i);
        if (loc == 2) return actChamber(i);
        return actGate(i);
    }

    // Risky repeat actions: only in the Passage and District, and only sometimes, can start a random fight.
    static String riskyRepeat(String flavor) {
        if (loc <= 1 && rng.nextInt(100) < 35) {
            int type = (loc == 1 && rng.nextBoolean()) ? SHARDLING : HOLLOW;
            return startBattle(type, 0, flavor + "\nSomething stirs in response.");
        }
        return flavor;
    }

    static String actPassage(int i) {
        if (i == 1) {
            if (collect(0)) return "You press your palm to the glowing crystal. It hums, and a voice spills out.\n" + shardText(0);
            return "The crystal has gone dim. You already carry its memory.";
        } else if (i == 2) {
            if (!done.contains("hollow"))
                return startBattle(HOLLOW, 1, "You follow the dragging sound into a bend of the tunnel. A hunched figure in a ragged cloak turns toward you, "
                    + "its cracked porcelain mask glinting violet.\n!! TUTORIAL: a raised arm warns of a HEAVY strike. PARRY it, or DODGE.");
            return riskyRepeat("You listen again. Distant scraping, nothing more... for now.");
        } else if (i == 3) {
            if (done.add("alcove")) {
                collect(1);
                resonance++;
                return "Behind fallen stones you find a small alcove, lined with scratched drawings of a garden. A shard lies in the middle. "
                    + "You hold it gently, and something deep in you eases. (Resonance rises.)\n" + shardText(1);
            }
            return riskyRepeat("The alcove holds only dust and old bones.");
        } else if (i == 4) {
            if (found[0] && done.contains("hollow")) return advance();
            return "A curtain of pale roots seals the way onward and will not part. Objectives remain unfinished.";
        }
        return "Nothing happens.";
    }

    static String actDistrict(int i) {
        if (i == 1) {
            if (collect(2)) return "A terminal still flickers in a ruined lab. You wipe the dust away and press the glowing key.\n" + shardText(2);
            return "The terminal only repeats the same record now.";
        } else if (i == 2) {
            if (!done.contains("shardling"))
                return startBattle(SHARDLING, 2, "The clock tower door hangs open. Inside, glass chimes. A faceless creature floats in a storm of crystal shards.\n"
                    + "!! HINT: when its crystals fold into a shell, only PIERCE cracks it. When they fan out, expect a sweeping volley.");
            return riskyRepeat("The tower is silent apart from the soft chime of settling glass.");
        } else if (i == 3) {
            if (done.add("archive")) {
                collect(3);
                resonance++;
                return "Behind a toppled shelf you find a sealed drawer. Inside lies a single shard, warm against your hand. (Resonance rises.)\n" + shardText(3);
            }
            return riskyRepeat("The archive is picked clean. Paper dust sifts down like snow.");
        } else if (i == 4) {
            if (!done.contains("remnant"))
                return startBattle(REMNANT, 3, "OPTIONAL CHALLENGE. You lean over a well choked with roots. Below, something enormous shifts. "
                    + "Bone branches and tangled roots haul up a pulsing crystal core.");
            return "The well breathes quietly. The lullaby has stopped.";
        } else if (i == 5) {
            if (found[2] && done.contains("shardling")) return advance();
            return "The tunnel beyond the district is blocked by a collapsed archway. Objectives remain unfinished.";
        }
        return "Nothing happens.";
    }

    static String actChamber(int i) {
        if (i == 1) {
            if (collect(5)) {
                resonance++;
                return "You wipe soot from the nearest pillar and read the carved words. (Resonance rises.)\n" + shardText(5);
            }
            return "The inscription is burned into your mind already.";
        } else if (i == 2) {
            if (done.contains("warden")) return "Only silence remains.";
            if (done.add("speak")) {
                resonance++;
                return "You do not raise your weapon. 'I do not know what I am,' you say. The Warden is silent a long time. "
                    + "'Neither did I,' it says. 'Duty is what we hold when we cannot be sure.' (Resonance rises.)";
            }
            return "The Warden says nothing more. Its crystal watches you.";
        } else if (i == 3) {
            if (done.contains("warden")) return "The Warden has already fallen.";
            return startBattle(WARDEN, 4, "The chains snap taut. The Warden rises, stone grinding, and the whole chamber shakes.\n"
                + "!! HINT: its slam is slow and obvious. Interrupt its charge only when its posture is already weakened.");
        } else if (i == 4) {
            if (done.contains("warden")) return advance();
            return "The great doors remain sealed while the Warden stands. Objectives remain unfinished.";
        }
        return "Nothing happens.";
    }

    static String actGate(int i) {
        if (i >= 1 && i <= 3) {
            if (done.contains("pillar" + i)) return "The pillar's memory has already passed through you.";
            if (i > 1 && !done.contains("pillar" + (i - 1))) return "This pillar stays dark. Perhaps the earlier one must be touched first.";
            done.add("pillar" + i);
            collect(6 + i);
            return "You lay your hand on the pillar. Light pours into you.\n" + shardText(6 + i);
        } else if (i == 4) {
            if (found[10]) return "The hum has settled into a quiet, steady note.";
            if (resonance >= 6 && hiddenCount() == 3) {         // special discovery for the secret ending
                collect(10);
                resonance++;
                return "Because you have listened to the forgotten places, the hum resolves into words. A shard rises out of the silver pool.\n" + shardText(10);
            }
            return "The hum shifts beneath your hearing, as though a hidden voice waits for someone who has listened more closely to the Echoes and to the forgotten places.";
        } else if (i == 5) {
            if (done.contains("pillar1") && done.contains("pillar2") && done.contains("pillar3")) { approachGate(); return ""; }
            return "The Gate does not answer yet. All three pillars must be touched first.";
        }
        return "Nothing happens.";
    }

    static String advance() {
        leave = true;                                            // the exploration loop ends; playGame moves to the next location
        return "You pass onward.";
    }

    static String restText() {
        String s;
        if (!restUsed[loc]) {
            restUsed[loc] = true;
            int before = hp;
            hp = Math.min(MAX_HP, hp + REST_HEAL);
            s = "You rest in a quiet corner and your light steadies. (+" + (hp - before) + " HP)";
        } else s = "You have already rested here. You cannot rest again.";
        String bond = resonance < 3 ? "faint" : (resonance < 6 ? "stirring" : "strong");
        return s + "\nYour bond with the Echoes is " + bond + " (Resonance " + resonance + ").";
    }

    static String showMemories() {
        StringBuilder sb = new StringBuilder("MEMORIES (" + shardCount() + "/" + SHARD_TOTAL + ")\n");
        for (int i = 0; i < found.length; i++)
            if (found[i]) sb.append("\n").append("<*>").append(SHARD_NAMES[i]).append("\n").append(SHARD_TEXTS[i]);
        if (shardCount() == 0) sb.append("\nYou hold no memories yet.");
        clearScreen();
        banner("MEMORIES", "", WHITE);
        panel(sb.toString());
        border();
        pause();
        return "You turn back to the " + LOCATION_NAMES[loc] + ".";
    }

    static String mark(boolean ok) { return ok ? "[x] " : "[ ] "; }

    static String showObjectives() {
        String s = "";
        if (loc == 0) s = mark(found[0]) + "Examine the glowing crystal\n" + mark(done.contains("hollow")) + "Face what stirs in the passage\nOptional: search the alcove";
        else if (loc == 1) s = mark(found[2]) + "Read the research terminal\n" + mark(done.contains("shardling")) + "Face the creature in the clock tower\nOptional: the archive, the root well";
        else if (loc == 2) s = mark(done.contains("warden")) + "Defeat the Warden\nOptional: the inscription, speak to the Warden";
        else s = mark(done.contains("pillar1")) + "Touch the first pillar\n" + mark(done.contains("pillar2")) + "Touch the second pillar\n"
                + mark(done.contains("pillar3")) + "Touch the third pillar\n[ ] Approach the Gate and decide\nOptional: listen to the hum";
        clearScreen();
        banner("OBJECTIVES", LOCATION_NAMES[loc], WHITE);
        panel(s);
        border();
        pause();
        return "You gather your thoughts.";
    }

    // ================= 9. BATTLE =================
    // Round order: enemy telegraphs -> player defends -> attack resolves -> player attacks -> check results.
    static String startBattle(int type, int tag, String intro) {
        curType = type;
        curTag = tag;
        while (true) {
            int result = fight(type, intro);
            if (result == LOST) {
                drawDefeat();
                if (choose("The light remembers you.", "Try again", "Return to title") == 1) { quitToTitle = true; return ""; }
                hp = MAX_HP;
                intro = "You gather your fading light and rise to face it again.";
                continue;
            }
            return afterBattle(result == SPARED);
        }
    }

    static int fight(int type, String intro) {
        enemy = new Enemy(type);
        enemyAnim = IDLE; playerPose = 0; offBalance = false; opening = false;
        note = "";
        drawBattle(intro + "\nA " + enemy.name + " bars your way!", 0);
        pause();
        while (true) {
            if (!enemy.staggered && defenseTurn() == LOST) return LOST;      // a staggered enemy loses its attack
            int r = offenseTurn();
            if (r != CONTINUE) return r;
        }
    }

    static String telegraph() {
        String[][] t = {
            {"The Hollow raises one long sleeve high above its head. HEAVY strike!", "The Hollow spins, both ragged arms flailing wide. Wide SWEEP!", "The Hollow hunches and coils, ready to throw itself at you. CHARGE!"},
            {"The Shardling's crystals gather. HEAVY strike!", "The Shardling's crystals fan out like blades. Wide SWEEP! (Its shards are open.)", "The Shardling folds its crystals into a tight shell and drifts forward. CHARGE! (Shell closed: Pierce cracks it.)"},
            {"The Warden lifts its stone fist toward the ceiling; chains groan. HEAVY slam!", "The Warden drags its chains in a wide arc. Wide SWEEP!", "The Warden's chest crystal flares as it lurches forward. CHARGE!"},
            {"The Remnant's roots coil upward and rattle. HEAVY strike!", "Bone branches whip outward, trembling. Wide SWEEP!", "The Remnant's core pulses red and its roots dig in. CHARGE!"}};
        String s = t[enemy.type][enemy.attack];
        if (enemy.type == WARDEN && enemy.attack == CHARGE)
            s += canInterrupt() ? " Its stance wavers: it can be interrupted." : " Its stance is firm: too strong to interrupt.";
        return s;
    }

    static boolean canInterrupt() { return enemy.type != WARDEN || enemy.posture <= enemy.maxPosture / 2; }

    static int defenseTurn() {
        enemy.attack = enemy.pattern[enemy.turn % enemy.pattern.length];
        enemy.defensivePose = (enemy.type == SHARDLING && enemy.turn % 2 == 1);
        enemy.turn++;
        enemyAnim = PREPARE;
        playerPose = 0;
        drawBattle((note.isEmpty() ? "" : note + "\n") + "!! " + telegraph(), 0);
        int c = choose("Defend:", "Parry   (counters heavy strikes)", "Dodge   (evades, makes an opening)", "Interrupt (stops a charge)");
        playerPose = c + 1;
        enemyAnim = ATTACK;
        if (useEffects) { drawBattle("The " + enemy.name + " strikes!", 0); sleep(450); }
        return resolveDefense(c, enemy.attack);
    }

    // Defense results are fixed rules, not luck. Returns LOST if the player's HP reaches 0.
    static int resolveDefense(int c, int att) {
        int dmg = enemy.damage, post = 0;
        String msg;
        if (c == 0) {                                           // PARRY
            if (att == HEAVY) { dmg = 0; post = 4; msg = "PERFECT PARRY! You turn the blow aside and its posture cracks (-4 posture)."; }
            else { dmg = dmg / 2; post = 2; msg = "You catch only part of the attack. Half damage taken, a little posture knocked loose (-2)."; }
        } else if (c == 1) {                                    // DODGE
            if (att == CHARGE) msg = "You dodge too early and the charge tracks you.";
            else { dmg = 0; opening = true; msg = "You slip aside! Its attack misses and leaves an OPENING: your next strike hits harder."; }
        } else {                                                // INTERRUPT
            if (att == CHARGE && canInterrupt()) { dmg = 0; post = 3; msg = "You cut the charge short! (-3 posture)"; }
            else if (att == CHARGE) msg = "You strike at the charge but its stance is too firm. It crashes through you.";
            else msg = "You cannot interrupt that kind of attack. It hits you full force.";
        }
        if (dmg > 0 && offBalance) { dmg += 2; msg += " (Off balance: +2 damage.)"; }
        offBalance = false;
        playerPose = 0;
        hp = Math.max(0, hp - dmg);
        enemy.posture = Math.max(0, enemy.posture - post);
        if (dmg > 0) msg += " You take " + dmg + " damage.";
        note = msg;
        enemyAnim = RECOVER;
        if (hp <= 0) return LOST;
        if (enemy.posture <= 0 && !enemy.staggered) { enemy.staggered = true; note += "\nThe " + enemy.name + " is STAGGERED!"; }
        return CONTINUE;
    }

    static int offenseTurn() {
        boolean wasStaggered = enemy.staggered;
        enemyAnim = wasStaggered ? STAGGER : IDLE;
        playerPose = 0;
        List<String> options = new ArrayList<>(Arrays.asList("Slash   (steady damage)", "Pierce  (cracks shells)", "Slam    (big posture, risky)"));
        if (wasStaggered && enemy.mercy) options.add("Mercy   (spare it)");
        String intro = wasStaggered ? "The " + enemy.name + " is staggered and open! Strike for DOUBLE damage, or show mercy." : "Your turn.";
        drawBattle((note.isEmpty() ? "" : note + "\n") + intro, 0);
        int c = chooseList("Attack:", options);
        if (c == 3) return mercy();
        if (useEffects) for (int f = 1; f <= 4; f++) { drawBattle("You strike!", f); sleep(100); }   // sword slash frames
        return resolveOffense(c, wasStaggered);
    }

    static int resolveOffense(int c, boolean wasStaggered) {
        boolean shell = enemy.type == SHARDLING && enemy.defensivePose && !wasStaggered;
        int dmg, post;
        if (c == 0) { dmg = shell ? 2 : 5; post = shell ? 0 : 2; }
        else if (c == 1) { dmg = shell ? 7 : 4; post = shell ? 4 : 1; }
        else { dmg = shell ? 1 : 3; post = shell ? 2 : 5; offBalance = true; }
        if (opening) { dmg += 2; post += 2; opening = false; }   // the opening bonus is used once
        if (wasStaggered) dmg *= 2;
        enemy.hp = Math.max(0, enemy.hp - dmg);
        enemy.posture = Math.max(0, enemy.posture - post);
        String msg = "Your " + OFFENSE_NAMES[c] + " hits for " + dmg + " damage and " + post + " posture.";
        if (shell && c == 1) msg += " The shell cracks!";
        else if (shell) msg += " The shell absorbs most of it.";
        if (c == 2) msg += " You are off balance.";
        note = msg;
        if (enemy.hp <= 0) return WON;
        if (wasStaggered) { enemy.staggered = false; enemy.posture = enemy.maxPosture; note += "\nThe " + enemy.name + " recovers its footing."; }
        else if (enemy.posture <= 0) { enemy.staggered = true; note += "\nThe " + enemy.name + " is STAGGERED!"; }
        return CONTINUE;
    }

    static int mercy() {
        note = "You lower your blade. The " + enemy.name + " lifts its head and waits.";
        return SPARED;
    }

    static void drawBattle(String msg, int sword) {
        clearScreen();
        banner("ENCOUNTER", enemy.name.toUpperCase(), RED);
        char[][] c = newCanvas();
        stamp(c, new String[]{"_.,._,  .,_ ,.__,  ._,.  ,_.,_.,."}, CANVAS_H - 1, 0, false);
        stamp(c, playerSprite(), CANVAS_H - PLAYER.length - 1, 2 + (playerPose == 2 ? -2 : 0), true);
        String[] es = enemySprite();
        stamp(c, es, CANVAS_H - 1 - es.length, ENEMY_COL + (enemyAnim == ATTACK ? -8 : 0), true);
        if (sword > 0) stamp(c, new String[]{"-==>"}, CANVAS_H - 5, 4 + sword * 3, true);   // the sword moves each frame
        printCanvas(c, GREY);
        border();
        line("YOU       HP " + bar(hp, MAX_HP, 10) + " " + hp + "/" + MAX_HP, WHITE, false);
        line(String.format("%-9s HP %s %d/%d", enemy.name.toUpperCase(), bar(enemy.hp, enemy.maxHp, 10), enemy.hp, enemy.maxHp), RED, false);
        line("POSTURE    " + bar(enemy.posture, enemy.maxPosture, 10) + (enemy.staggered ? " STAGGERED" : ""), enemy.staggered ? VIOLET : CYAN, false);
        border();
        panel(msg);
        border();
    }

    // The enemy picture changes with its state: idle, preparing (arm/shape warning), attacking, staggered, defeated.
    static String[] enemySprite() {
        if (enemyAnim == DEFEATED) return new String[]{"  .  '  .  ,", " '  .   '  . ", "  .  '  .  '"};
        String[] base = (enemy.type == SHARDLING && enemy.defensivePose && enemyAnim != STAGGER) ? SHARDLING_SHELL : ENEMY_ART[enemy.type];
        List<String> s = new ArrayList<>(Arrays.asList(base));
        if (enemyAnim == STAGGER) s.add(0, "  ~  x  ~  x  ~");
        else if (enemyAnim == ATTACK) s.add(0, "!! STRIKE !!");
        else if (enemyAnim == PREPARE) {
            String[] warn = {"    (@@) !!", "<~~~~ !! ~~~~>", ">>>>>>  !!"};
            s.add(0, warn[enemy.attack]);
        }
        return s.toArray(new String[0]);
    }

    static void drawDefeat() {
        clearScreen();
        banner("YOU FALL", "", RED);
        char[][] c = newCanvas();
        stamp(c, new String[]{"      .  '  .   *  .  '   .", "    '    .    +    .    '", "  .   .-.   '    .    .", "  '   (_)   .   '    '"}, 2, 0, false);
        stamp(c, new String[]{"_,._.,_ ___ ,._.,_.,_,._.,_.,_"}, 7, 0, false);
        printCanvas(c, GREY);
        border();
        panel(note + "\nYour vision blurs and the cyan light gutters out...");
        border();
    }

    // Applies rewards after a win or a mercy. Returns the text shown back in exploration.
    static String afterBattle(boolean spared) {
        String name = enemy.name;
        int tag = curTag;
        enemyAnim = DEFEATED;
        drawBattle(note + "\n" + (spared ? "The " + name + " dissolves into drifting light, calmer than before. (Resonance rises.)"
            : "The " + name + " breaks apart into dull sparks. You feel a hollow ache."), 0);
        pause();
        String base = note + "\n" + (spared ? "The " + name + " fades, calmer than before. (Resonance rises.)" : "The " + name + " breaks apart into dull sparks.");
        enemy = null;
        enemyAnim = IDLE;
        hp = Math.min(MAX_HP, hp + WIN_HEAL);
        if (spared) resonance += (tag == 0) ? 1 : 2;
        if (tag == 1) {
            done.add("hollow");
            return base + "\nIts last whisper: 'Same... you are the same as us...' You tell yourself you misheard.";
        } else if (tag == 2) {
            done.add("shardling");
            return base + "\nThe chiming stops. Deeper in the tower, a lock clicks open: the way out of the district.";
        } else if (tag == 3) {
            done.add("remnant");
            collect(4);
            return base + "\nIn the roots lies a shard.\n" + shardText(4);
        } else if (tag == 4) {
            done.add("warden");
            collect(6);
            storyPage(base + "\nThe Warden crashes to its knees. The helmet splits and nothing is inside but light. The crystal drifts into your hands.\n" + shardText(6));
            return "The far wall grinds open to reveal a stairway descending toward the Memory Gate. You are connected to this somehow, and the Warden knew it.";
        }
        return base;
    }

    // ================= 10. ENDINGS =================
    static void approachGate() {
        storyPage("You step before the Gate. The silver surface stills and shows your own reflection: the pale mask, the cyan glow.");
        storyPage("'You are an Echo,' the Gate says, in a voice made of many voices. 'A memory that held its shape. The apprentice's imprint gathered the pieces of a life and "
            + "refused to fade. That is why Echoes gather to you, and why you could walk through the dead city as one of them. Every Echo is bound to me. Now decide what I become.'");
        finalChoice();
    }

    // Ending conditions: Restore needs Resonance >= 5. The Whole needs the secret shard and Resonance >= 8.
    // Sever and Let the Gate sleep are always available, so a low-Resonance player can always finish.
    static void finalChoice() {
        ending = -1;
        while (ending < 0) {
            List<String> options = new ArrayList<>();
            List<Integer> ids = new ArrayList<>();
            if (resonance >= 5) { options.add("Restore the Echoes"); ids.add(0); }
            options.add("Sever the link"); ids.add(1);
            options.add("Let the Gate sleep"); ids.add(2);
            if (found[10] && resonance >= 8) { options.add("Become the Whole"); ids.add(3); }
            drawLocation("THE FINAL DECISION.\nRestore: return every memory to the Echoes so they may choose who to be.\nSever: destroy the failing link and its power.\n"
                + "Let the Gate sleep: walk away and leave the truth unfinished."
                + (resonance >= 5 ? "" : "\n(Your bond with the Echoes is too faint to restore them.)"));
            ending = ids.get(chooseList("Decide:", options));
        }
        leave = true;
    }

    static void showEnding(int id) {
        String[] texts = {
            "You place both hands on the Gate and offer it everything you remember, not as a record but as a gift. The binding unravels gently. In the silent streets, Hollows lift "
                + "their masks, Shardlings let their crystals settle like snow, and each Echo remembers a name and chooses what to become. You are not the apprentice, nor the Gate's creature. "
                + "You are the one who opened the door, and you walk out into a dawn that has been waiting.",
            "You tear the link apart. The Gate screams and goes dark, and the failing system dies with it. Echoes that were only borrowed memories scatter into light; those who held their shape "
                + "are left alone, frightened, unable to be mended. You stand in the silence, free of the Gate and of the thing that made you, knowing you could have chosen otherwise.",
            "You turn away. The Gate dims, neither broken nor mended. Behind the silver, the truth stays unspoken: who the apprentice was, what the Echoes might have become. "
                + "You climb back into the passage with a glow in your chest and no name. Perhaps someday you will remember. Perhaps someone will remember you.",
            "You read the apprentice's letter aloud and the Gate listens. Instead of binding memories to a failing machine, you become the thread that holds them: a place where memories "
                + "can rest and return to those who wish to carry them. The Gate closes gently and the pillars fall silent. The Echoes gather around you, not as servants or ghosts but as kin. "
                + "Nothing is lost, and no one is forced to serve. The Whole, at last."};
        String[] colors = {CYAN, RED, GREY, WHITE};
        transition("The end");
        clearScreen();
        banner("ENDING", ENDING_NAMES[id], colors[id]);
        char[][] c = newCanvas();
        stamp(c, ENDING_ART[id], 1, 4, false);
        printCanvas(c, colors[id]);
        border();
        panel(texts[id] + "\nThank you for playing SOULBOUND.");
        border();
        pause();
    }
}
