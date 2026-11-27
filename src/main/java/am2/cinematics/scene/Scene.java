package am2.cinematics.scene;

import am2.cinematics.ritual.Minigame;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Linha do tempo de uma cinemática. Não depende do Minecraft: o servidor valida,
 * o cliente valida de novo e toca. Posições são locais à âncora
 * (x = direita, y = cima, z = frente).
 */
public final class Scene {
    public static final Charset UTF8 = Charset.forName("UTF-8");
    public static final int MAX_BYTES = 30000;
    public static final int MAX_DURATION = 20 * 180;
    public static final int MAX_ENTRIES = 256;
    public static final int MAX_TEXT = 128;
    public static final double MAX_CAMERA_DISTANCE = 128.0;
    public static final double MAX_LOOK_DISTANCE = 128.0;
    public static final int MAX_ACTORS = 48;
    public static final int MAX_POINTS = 64;
    public static final int MAX_PARTICLES_PER_TICK = 20;
    public static final int MAX_RUNES = 64;
    public static final int MAX_GATES = 8;
    /** Intervalo entre batidas do coração (orbe e veias). */
    public static final int HEARTBEAT_TICKS = 18;

    public enum Ease { LINEAR, IN, OUT, IN_OUT }
    public enum Type { FADE, TITLE, SHAKE, SOUND, PARTICLES, RUNES, LIGHTNING }
    public enum ActorType { BOOK, CIRCLE, GLOW, ORB, VEINS, EYES, CHALK, CANDLES, ITEM, ROOTS, RING, NIGHT, WORLDVEINS, PILLAR, WAVE, RAIN;
        /** Efeitos presos ao corpo do jogador, sem posição própria. */
        public boolean onBody() {
            return this == VEINS || this == EYES;
        }

        /** Tem um lugar no mundo ({@code pos} ou {@code attach}). */
        public boolean placed() {
            return !onBody() && this != NIGHT;
        }
    }
    /** Onde um ponto de ator se prende; {@code pos} passa a ser o deslocamento. */
    public enum Attach { NONE, RIGHT_HAND, LEFT_HAND, HEART }

    public static final class Key {
        public int t;
        public final double[] pos = new double[3];
        public final double[] look = new double[3];
        public double fov = 70, roll = 0;
        public boolean cut;
        public Ease ease = Ease.LINEAR;
    }

    public static final class Event {
        public Type type;
        public int t, duration;
        /** fade: alfa inicial/final. */
        public double from, to;
        public int color;
        public String text = "", subtitle = "", sound = "";
        public double y = 0.72, strength = 1, pitch = 1;
        public boolean glitch;
        /** particles: nome vanilla, origem local, variações e quantidade por tick. */
        public String particle = "";
        public final double[] pos = new double[3], spread = new double[3];
        public final double[] velocity = new double[3], velocitySpread = new double[3];
        public int count;
        /** runes: saem de {@code pos} (com {@code spread}) e voam em arco até {@code target} ("to" no JSON). */
        public final double[] target = new double[3];
        public int travel;
        public double arc, scale;
    }

    /** Pausa para um minigame, usada só quando a cena é um ritual de verdade. */
    public static final class Gate {
        public final int t;
        public final Minigame.Type game;

        private Gate(int t, Minigame.Type game) {
            this.t = t;
            this.game = game;
        }
    }

    /** Estado de um ator em um instante. */
    public static final class ActorKey {
        public int t;
        public final double[] pos = new double[3];
        /** scale é o tamanho (no círculo e no brilho, o raio em blocos). */
        public double scale = 1, alpha = 1;
        /** open: abertura do livro ou fração desenhada do círculo. */
        public double open = 1;
        /** Livro: para onde as páginas olham (0 = de volta para a âncora) e inclinação para cima. */
        public double yaw = 0, tilt = 65;
        public Attach attach = Attach.NONE;
        /** worldveins: posição do pulso que volta (0 = nas pontas, 1 = no centro; -1 = sem pulso). */
        public double pulse = -1;
    }

    /** Objeto 3D que existe só durante a cena e só para quem assiste. */
    public static final class Actor {
        public final ActorType type;
        public final int color;
        /** Graus por tick, para o círculo. */
        public final double spin;
        public final List<ActorKey> keys;
        /** chalk e candles: posições [x, z] no chão, relativas ao ator. */
        public final List<double[]> points;
        /** item: nome de registro ("mod:nome") e metadado. */
        public final String item;
        public final int meta;
        /** worldveins: cor do pulso que volta. */
        public final int pulseColor;
        private final List<Pose.Key> pose;
        private final double[] stand;

        private Actor(ActorType type, int color, double spin, List<ActorKey> keys, List<Pose.Key> pose, double[] stand,
                List<double[]> points, String item, int meta, int pulseColor) {
            this.pulseColor = pulseColor;
            this.pose = pose;
            this.stand = stand;
            this.points = Collections.unmodifiableList(points);
            this.item = item;
            this.meta = meta;
            this.type = type;
            this.color = color;
            this.spin = spin;
            this.keys = Collections.unmodifiableList(keys);
        }

        /** @return falso se o ator não está em cena (antes do primeiro ou depois do último ponto). */
        public boolean sample(double time, ActorKey out) {
            if (time < keys.get(0).t || time > keys.get(keys.size() - 1).t) return false;
            int i = 0;
            while (i + 2 < keys.size() && keys.get(i + 1).t <= time) i++;
            ActorKey a = keys.get(i), b = keys.get(Math.min(i + 1, keys.size() - 1));
            double u = b.t == a.t ? 0 : apply(Ease.IN_OUT, Math.max(0, Math.min(1, (time - a.t) / (b.t - a.t))));
            // Pontos presos à mão são resolvidos agora, então o ator acompanha o gesto.
            double[] from = resolve(a, time), to = resolve(b, time);
            for (int axis = 0; axis < 3; axis++) out.pos[axis] = mix(from[axis], to[axis], u);
            out.scale = mix(a.scale, b.scale, u);
            out.alpha = mix(a.alpha, b.alpha, u);
            out.open = mix(a.open, b.open, u);
            out.yaw = mix(a.yaw, b.yaw, u);
            out.tilt = mix(a.tilt, b.tilt, u);
            // Ligar ou desligar o pulso é instantâneo; só o percurso é interpolado.
            out.pulse = a.pulse < 0 || b.pulse < 0 ? a.pulse : mix(a.pulse, b.pulse, u);
            return true;
        }

        private double[] resolve(ActorKey key, double time) {
            if (key.attach == Attach.NONE) return key.pos;
            Pose.Key now = new Pose.Key();
            double[] point = new double[3];
            Pose.Key body = Pose.sample(pose, time, now) ? now : null;
            if (key.attach == Attach.HEART) Pose.heart(body, point);
            else Pose.hand(body, key.attach == Attach.RIGHT_HAND, point);
            for (int axis = 0; axis < 3; axis++) point[axis] += key.pos[axis] + (stand == null ? 0 : stand[axis]);
            return point;
        }

        private static double mix(double a, double b, double u) {
            return a + (b - a) * u;
        }
    }

    /** Resultado de uma amostragem; reutilizável entre frames. */
    public static final class Sample {
        public final double[] pos = new double[3];
        public final double[] look = new double[3];
        public double fov, roll;
    }

    public final int duration;
    public final double letterbox;
    public final boolean collide;
    /** O servidor vira a cena (e o jogador-âncora) para o lado com mais espaço. */
    public final boolean align;
    public final List<Key> keys;
    public final List<Event> events;
    public final List<Actor> actors;
    /** Pose do jogador-âncora (vazia = animação normal do jogo). */
    public final List<Pose.Key> pose;
    /** Esconde armadura e item na mão do jogador-âncora, só no visual. */
    public final boolean hideGear;
    /**
     * Se não for nulo, o jogador-âncora é colocado neste ponto local (o servidor o
     * leva até lá) e a cena se alinha aos blocos.
     */
    public final double[] stand;
    /** Bloco (nome de registro) que o comando procura por perto para ancorar a cena, ou vazio. */
    public final String altar;
    /** Pausas declaradas pela cena; o ritual usa {@link #ritualGates()}. */
    public final List<Gate> gates;

    private Scene(int duration, double letterbox, boolean collide, boolean align, List<Key> keys,
            List<Event> events, List<Actor> actors, List<Pose.Key> pose, boolean hideGear, double[] stand,
            String altar, List<Gate> gates) {
        this.gates = Collections.unmodifiableList(gates);
        this.altar = altar;
        this.stand = stand;
        this.pose = Collections.unmodifiableList(pose);
        this.hideGear = hideGear;
        this.duration = duration;
        this.letterbox = letterbox;
        this.collide = collide;
        this.align = align;
        this.keys = Collections.unmodifiableList(keys);
        this.events = Collections.unmodifiableList(events);
        this.actors = Collections.unmodifiableList(actors);
    }

    /** Pulso duplo, como um coração: 0 em repouso, perto de 1 no pico. */
    public static double heartbeat(double ticks) {
        double phase = ((ticks % HEARTBEAT_TICKS) + HEARTBEAT_TICKS) % HEARTBEAT_TICKS;
        double first = Math.min(phase, HEARTBEAT_TICKS - phase), second = phase - 5;
        return Math.min(1, Math.exp(-first * first / 3) + 0.7 * Math.exp(-second * second / 3));
    }

    /**
     * Posição local de uma runa. A trajetória depende só do índice e do tempo,
     * então é a mesma em qualquer FPS e para todos.
     *
     * @return quanto do caminho ela já fez (0 a 1), ou -1 se não está no ar
     */
    public static double rune(Event e, int index, double time, double[] out) {
        double stagger = e.count < 2 ? 0 : (double) (e.duration - e.travel) / (e.count - 1);
        double u = (time - e.t - index * stagger) / e.travel;
        if (u < 0 || u > 1) return -1;
        Random random = new Random(index * 7919L + e.color);
        // Acelera ao chegar, como se fosse puxada para dentro.
        double s = u * u * (1.6 - 0.6 * u);
        double angle = random.nextDouble() * Math.PI * 2, bend = e.arc * (0.4 + 0.6 * random.nextDouble());
        double[] lift = { Math.cos(angle) * bend, (0.25 + 0.75 * Math.abs(Math.sin(angle))) * bend, Math.sin(angle) * bend * 0.5 };
        for (int axis = 0; axis < 3; axis++) {
            double start = e.pos[axis] + (random.nextDouble() * 2 - 1) * e.spread[axis];
            double control = (start + e.target[axis]) / 2 + lift[axis];
            out[axis] = (1 - s) * (1 - s) * start + 2 * (1 - s) * s * control + s * s * e.target[axis];
        }
        return u;
    }

    /** A câmera sobe tanto que nuvens e neblina atrapalhariam a visão. */
    public boolean looksFromAbove() {
        for (Key k : keys) {
            if (k.pos[1] > 40) return true;
        }
        return false;
    }

    /** A cena mexe no corpo do jogador-âncora (pose, equipamento ou efeitos nele)? */
    public boolean actsOnBody() {
        if (hideGear || !pose.isEmpty()) return true;
        for (Actor actor : actors) {
            if (actor.type.onBody()) return true;
        }
        return false;
    }

    /** Converte um deslocamento local (direita, cima, frente) em deslocamento no mundo. */
    public static void rotate(double[] local, double yawDegrees, double[] out) {
        double sin = Math.sin(Math.toRadians(yawDegrees)), cos = Math.cos(Math.toRadians(yawDegrees));
        double right = local[0], up = local[1], forward = local[2];
        out[0] = -cos * right - sin * forward;
        out[1] = up;
        out[2] = -sin * right + cos * forward;
    }

    public static Scene parse(String json) {
        if (json == null || json.getBytes(UTF8).length > MAX_BYTES) {
            throw new IllegalArgumentException("cena ausente ou maior que " + MAX_BYTES + " bytes");
        }
        try {
            return parseObject(new JsonParser().parse(json).getAsJsonObject());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("JSON inválido: " + e.getMessage());
        }
    }

    private static Scene parseObject(JsonObject root) {
        int duration = (int) number(root, "duration", -1, 1, MAX_DURATION);
        double letterbox = number(root, "letterbox", 0.12, 0, 0.3);
        boolean collide = bool(root, "collide", true);

        JsonArray cameraArray = array(root, "camera");
        if (cameraArray.size() == 0) throw new IllegalArgumentException("camera precisa de ao menos um ponto");
        List<Key> keys = new ArrayList<Key>();
        int previous = -1;
        for (JsonElement element : cameraArray) {
            JsonObject o = element.getAsJsonObject();
            Key k = new Key();
            k.t = (int) number(o, "t", -1, 0, duration);
            if (k.t <= previous) throw new IllegalArgumentException("camera.t deve ser crescente (t=" + k.t + ")");
            if (keys.isEmpty() && k.t != 0) throw new IllegalArgumentException("o primeiro ponto da camera deve ter t=0");
            previous = k.t;
            vector(o, "pos", k.pos, MAX_CAMERA_DISTANCE);
            vector(o, "look", k.look, MAX_LOOK_DISTANCE);
            k.fov = number(o, "fov", 70, 10, 150);
            k.roll = number(o, "roll", 0, -180, 180);
            k.cut = bool(o, "cut", false);
            k.ease = ease(string(o, "ease", "linear"));
            keys.add(k);
        }

        List<Event> events = new ArrayList<Event>();
        if (root.has("events")) {
            for (JsonElement element : array(root, "events")) {
                events.add(parseEvent(element.getAsJsonObject(), duration));
            }
        }
        List<Pose.Key> pose = new ArrayList<Pose.Key>();
        if (root.has("pose")) {
            for (JsonElement element : array(root, "pose")) {
                pose.add(parsePose(element.getAsJsonObject(), duration, pose.isEmpty() ? null : pose.get(pose.size() - 1)));
            }
        }

        double[] stand = null;
        if (root.has("stand")) {
            stand = new double[3];
            vector(root, "stand", stand, 16);
        }

        List<Actor> actors = new ArrayList<Actor>();
        if (root.has("actors")) {
            JsonArray actorArray = array(root, "actors");
            if (actorArray.size() > MAX_ACTORS) throw new IllegalArgumentException("mais de " + MAX_ACTORS + " atores");
            for (JsonElement element : actorArray) {
                actors.add(parseActor(element.getAsJsonObject(), duration, pose, stand));
            }
        }
        return new Scene(duration, letterbox, collide, bool(root, "align", false), keys, events, actors, pose,
                bool(root, "hideGear", false), stand, altarName(root), parseGates(root, duration));
    }

    private static List<Gate> parseGates(JsonObject root, int duration) {
        List<Gate> gates = new ArrayList<Gate>();
        if (!root.has("gates")) return gates;
        JsonArray array = array(root, "gates");
        if (array.size() > MAX_GATES) throw new IllegalArgumentException("mais de " + MAX_GATES + " pausas");
        int previous = 0;
        for (JsonElement element : array) {
            JsonObject o = element.getAsJsonObject();
            int t = (int) number(o, "t", -1, 1, duration - 1);
            if (t <= previous) throw new IllegalArgumentException("gates.t deve ser crescente (t=" + t + ")");
            previous = t;
            String name = string(o, "game", "");
            Minigame.Type game = null;
            for (Minigame.Type type : Minigame.Type.values()) {
                if (type.name().equalsIgnoreCase(name)) game = type;
            }
            if (game == null) throw new IllegalArgumentException("minigame desconhecido: " + name);
            gates.add(new Gate(t, game));
        }
        return gates;
    }

    /**
     * As pausas do ritual. Uma cena editada que não declare nenhuma ainda pede os
     * três minigames, espalhados por ela.
     */
    public List<Gate> ritualGates() {
        if (!gates.isEmpty()) return gates;
        List<Gate> spread = new ArrayList<Gate>();
        double[] at = { 0.12, 0.4, 0.75 };
        int previous = 0;
        for (int i = 0; i < at.length; i++) {
            int t = (int) (duration * at[i]);
            if (t <= previous || t >= duration) continue;
            spread.add(new Gate(t, Minigame.Type.values()[i]));
            previous = t;
        }
        return spread;
    }

    private static String altarName(JsonObject root) {
        String name = string(root, "altar", "");
        if (!name.isEmpty() && !name.matches("[A-Za-z0-9_.|-]{1,64}:[A-Za-z0-9_./|-]{1,64}")) {
            throw new IllegalArgumentException("altar precisa ser \"mod:bloco\": " + name);
        }
        return name;
    }

    private static Pose.Key parsePose(JsonObject o, int duration, Pose.Key previous) {
        Pose.Key key = new Pose.Key();
        if (previous != null) key.copy(previous);
        key.t = (int) number(o, "t", -1, 0, duration);
        if (previous != null && key.t <= previous.t) {
            throw new IllegalArgumentException("pose.t deve ser crescente (t=" + key.t + ")");
        }
        key.weight = number(o, "w", key.weight, 0, 1);
        key.head = number(o, "head", key.head, -80, 80);
        key.tremor = number(o, "tremor", key.tremor, 0, 10);
        key.lift = number(o, "lift", key.lift, 0, 8);
        arm(o, "right", key.right);
        arm(o, "left", key.left);
        return key;
    }

    private static void arm(JsonObject o, String name, double[] out) {
        if (!o.has(name)) return;
        JsonArray a = array(o, name);
        if (a.size() != 4) throw new IllegalArgumentException(name + " precisa de [x, y, z, cotovelo]");
        for (int i = 0; i < 4; i++) {
            double v = a.get(i).getAsDouble();
            if (!(v >= -180 && v <= 180)) throw new IllegalArgumentException(name + ": ângulo fora de -180 a 180");
            out[i] = v;
        }
    }

    private static Actor parseActor(JsonObject o, int duration, List<Pose.Key> pose, double[] stand) {
        String name = string(o, "type", "");
        ActorType type;
        if ("book".equals(name)) type = ActorType.BOOK;
        else if ("circle".equals(name)) type = ActorType.CIRCLE;
        else if ("glow".equals(name)) type = ActorType.GLOW;
        else if ("orb".equals(name)) type = ActorType.ORB;
        else if ("veins".equals(name)) type = ActorType.VEINS;
        else if ("eyes".equals(name)) type = ActorType.EYES;
        else if ("chalk".equals(name)) type = ActorType.CHALK;
        else if ("candles".equals(name)) type = ActorType.CANDLES;
        else if ("item".equals(name)) type = ActorType.ITEM;
        else if ("rain".equals(name)) type = ActorType.RAIN;
        else if ("roots".equals(name)) type = ActorType.ROOTS;
        else if ("ring".equals(name)) type = ActorType.RING;
        else if ("night".equals(name)) type = ActorType.NIGHT;
        else if ("worldveins".equals(name)) type = ActorType.WORLDVEINS;
        else if ("pillar".equals(name)) type = ActorType.PILLAR;
        else if ("wave".equals(name)) type = ActorType.WAVE;
        else throw new IllegalArgumentException("tipo de ator desconhecido: " + name);

        JsonArray array = array(o, "keys");
        if (array.size() == 0) throw new IllegalArgumentException("ator sem pontos");
        List<ActorKey> keys = new ArrayList<ActorKey>();
        ActorKey previous = null;
        for (JsonElement element : array) {
            JsonObject k = element.getAsJsonObject();
            ActorKey key = new ActorKey();
            key.t = (int) number(k, "t", -1, 0, duration);
            if (previous != null && key.t <= previous.t) {
                throw new IllegalArgumentException("os pontos do ator devem ter t crescente (t=" + key.t + ")");
            }
            // Campos omitidos repetem o ponto anterior.
            ActorKey base = previous == null ? new ActorKey() : previous;
            if (k.has("pos")) {
                vector(k, "pos", key.pos, MAX_CAMERA_DISTANCE);
                key.attach = attach(string(k, "attach", ""));
            } else if (k.has("attach")) {
                key.attach = attach(string(k, "attach", ""));
            } else if (previous != null) {
                System.arraycopy(base.pos, 0, key.pos, 0, 3);
                key.attach = base.attach;
            } else if (type.placed()) {
                throw new IllegalArgumentException("o primeiro ponto do ator precisa de pos ou attach");
            }
            key.scale = number(k, "scale", base.scale, 0, 160);
            key.alpha = number(k, "alpha", base.alpha, 0, 1);
            key.open = number(k, "open", base.open, 0, 1);
            key.yaw = number(k, "yaw", base.yaw, -3600, 3600);
            key.tilt = number(k, "tilt", base.tilt, -90, 90);
            key.pulse = number(k, "pulse", base.pulse, -1, 1);
            keys.add(key);
            previous = key;
        }
        List<double[]> points = new ArrayList<double[]>();
        if (type == ActorType.CHALK || type == ActorType.CANDLES) {
            JsonArray list = array(o, "points");
            if (list.size() == 0 || list.size() > MAX_POINTS) {
                throw new IllegalArgumentException("points precisa de 1 a " + MAX_POINTS + " posições");
            }
            for (JsonElement element : list) {
                JsonArray p = element.getAsJsonArray();
                double x = p.get(0).getAsDouble(), z = p.get(1).getAsDouble();
                if (p.size() != 2 || !(x * x + z * z <= 32 * 32)) throw new IllegalArgumentException("ponto [x, z] inválido");
                points.add(new double[] { x, z });
            }
        }
        String item = string(o, "item", "");
        if (type == ActorType.ITEM && !item.matches("[A-Za-z0-9_.|-]{1,64}:[A-Za-z0-9_./|-]{1,64}")) {
            throw new IllegalArgumentException("item precisa ser \"mod:nome\": " + item);
        }
        return new Actor(type, color(o, "ffffff"), number(o, "spin", 0, -30, 30), keys, pose, stand, points, item,
                (int) number(o, "meta", 0, 0, 32767), colorField(o, "pulseColor", "ffffff"));
    }

    private static Attach attach(String name) {
        if (name.isEmpty()) return Attach.NONE;
        if ("right_hand".equals(name)) return Attach.RIGHT_HAND;
        if ("left_hand".equals(name)) return Attach.LEFT_HAND;
        if ("heart".equals(name)) return Attach.HEART;
        throw new IllegalArgumentException("attach desconhecido: " + name);
    }

    private static int color(JsonObject o, String fallback) {
        return colorField(o, "color", fallback);
    }

    private static int colorField(JsonObject o, String name, String fallback) {
        String color = string(o, name, fallback);
        if (!color.matches("[0-9a-fA-F]{6}")) throw new IllegalArgumentException("cor deve ser RRGGBB: " + color);
        return Integer.parseInt(color, 16);
    }

    private static Event parseEvent(JsonObject o, int duration) {
        Event e = new Event();
        String type = string(o, "type", "");
        e.t = (int) number(o, "t", -1, 0, duration);
        if ("fade".equals(type)) {
            e.type = Type.FADE;
            e.duration = (int) number(o, "duration", -1, 1, duration);
            e.from = number(o, "from", -1, 0, 1);
            e.to = number(o, "to", -1, 0, 1);
            e.color = color(o, "000000");
        } else if ("title".equals(type)) {
            e.type = Type.TITLE;
            e.duration = (int) number(o, "duration", -1, 1, duration);
            e.text = string(o, "text", "");
            e.subtitle = string(o, "subtitle", "");
            e.y = number(o, "y", 0.72, 0, 1);
            e.glitch = bool(o, "glitch", false);
        } else if ("shake".equals(type)) {
            e.type = Type.SHAKE;
            e.duration = (int) number(o, "duration", -1, 1, duration);
            e.strength = number(o, "strength", 1, 0, 10);
        } else if ("particles".equals(type)) {
            e.type = Type.PARTICLES;
            e.duration = (int) number(o, "duration", -1, 1, duration);
            e.particle = string(o, "name", "");
            if (!e.particle.matches("[A-Za-z0-9_]{1,32}")) throw new IllegalArgumentException("partícula inválida: " + e.particle);
            e.count = (int) number(o, "count", 1, 1, MAX_PARTICLES_PER_TICK);
            vector(o, "pos", e.pos, MAX_CAMERA_DISTANCE);
            optionalVector(o, "spread", e.spread);
            optionalVector(o, "velocity", e.velocity);
            optionalVector(o, "velocitySpread", e.velocitySpread);
        } else if ("lightning".equals(type)) {
            e.type = Type.LIGHTNING;
            // Só a coluna importa: o raio cai na superfície.
            vector(o, "pos", e.pos, MAX_CAMERA_DISTANCE);
        } else if ("runes".equals(type)) {
            e.type = Type.RUNES;
            e.duration = (int) number(o, "duration", -1, 1, duration);
            e.travel = (int) number(o, "travel", -1, 1, e.duration);
            e.count = (int) number(o, "count", 1, 1, MAX_RUNES);
            e.arc = number(o, "arc", 1, 0, 8);
            e.scale = number(o, "scale", 0.25, 0.01, 2);
            e.color = color(o, "ffffff");
            vector(o, "pos", e.pos, MAX_CAMERA_DISTANCE);
            vector(o, "to", e.target, MAX_CAMERA_DISTANCE);
            optionalVector(o, "spread", e.spread);
        } else if ("sound".equals(type)) {
            e.type = Type.SOUND;
            e.sound = string(o, "name", "");
            if (!e.sound.matches("[a-z0-9_.:/-]{1,128}")) throw new IllegalArgumentException("nome de som inválido: " + e.sound);
            e.pitch = number(o, "pitch", 1, 0.5, 2);
        } else {
            throw new IllegalArgumentException("tipo de evento desconhecido: " + type);
        }
        return e;
    }

    /** Primeiro tick do plano (trecho entre cortes) que está no ar em {@code time}. */
    public int shotStart(double time) {
        int start = 0;
        for (Key k : keys) {
            if (k.cut && k.t <= time) start = k.t;
        }
        return start;
    }

    /** Último tick do plano que está no ar em {@code time}. */
    public int shotEnd(double time) {
        for (Key k : keys) {
            if (k.cut && k.t > time) return k.t - 1;
        }
        return duration;
    }

    /** Amostra a câmera no instante {@code time} (em ticks, fracionário). */
    public void sample(double time, Sample out) {
        int i = 0;
        while (i + 1 < keys.size() && keys.get(i + 1).t <= time) i++;
        Key a = keys.get(i);
        Key b = i + 1 < keys.size() ? keys.get(i + 1) : null;
        // Antes de um corte, o plano anterior fica parado no último ponto.
        if (b == null || b.cut || time <= a.t) {
            copy(a, out);
            return;
        }
        double h = b.t - a.t;
        double u = apply(b.ease, (time - a.t) / h);
        for (int axis = 0; axis < 3; axis++) {
            out.pos[axis] = hermite(i, axis, false, u, h);
            out.look[axis] = hermite(i, axis, true, u, h);
        }
        out.fov = a.fov + (b.fov - a.fov) * u;
        out.roll = a.roll + (b.roll - a.roll) * u;
    }

    private static void copy(Key k, Sample out) {
        System.arraycopy(k.pos, 0, out.pos, 0, 3);
        System.arraycopy(k.look, 0, out.look, 0, 3);
        out.fov = k.fov;
        out.roll = k.roll;
    }

    private double hermite(int i, int axis, boolean look, double u, double h) {
        double p0 = value(i, axis, look), p1 = value(i + 1, axis, look);
        double m0 = tangent(i, axis, look, (p1 - p0) / h);
        double m1 = tangent(i + 1, axis, look, (p1 - p0) / h);
        double u2 = u * u, u3 = u2 * u;
        return (2 * u3 - 3 * u2 + 1) * p0 + (u3 - 2 * u2 + u) * h * m0
                + (-2 * u3 + 3 * u2) * p1 + (u3 - u2) * h * m1;
    }

    /** Velocidade no ponto: média com os vizinhos do mesmo plano; zero ao entrar ou sair de uma pausa. */
    private double tangent(int i, int axis, boolean look, double segmentSlope) {
        Key k = keys.get(i);
        Key before = i > 0 && !k.cut ? keys.get(i - 1) : null;
        Key after = i + 1 < keys.size() && !keys.get(i + 1).cut ? keys.get(i + 1) : null;
        if (before != null && same(before, k, look)) return 0;
        if (after != null && same(k, after, look)) return 0;
        if (before == null || after == null) return segmentSlope;
        return (value(i + 1, axis, look) - value(i - 1, axis, look)) / (after.t - before.t);
    }

    private double value(int i, int axis, boolean look) {
        return look ? keys.get(i).look[axis] : keys.get(i).pos[axis];
    }

    private static boolean same(Key a, Key b, boolean look) {
        double[] x = look ? a.look : a.pos, y = look ? b.look : b.pos;
        return x[0] == y[0] && x[1] == y[1] && x[2] == y[2];
    }

    private static double apply(Ease ease, double u) {
        switch (ease) {
            case IN: return u * u;
            case OUT: return u * (2 - u);
            case IN_OUT: return u * u * (3 - 2 * u);
            default: return u;
        }
    }

    private static Ease ease(String name) {
        if ("linear".equals(name)) return Ease.LINEAR;
        if ("in".equals(name)) return Ease.IN;
        if ("out".equals(name)) return Ease.OUT;
        if ("inOut".equals(name)) return Ease.IN_OUT;
        throw new IllegalArgumentException("ease desconhecido: " + name);
    }

    private static JsonArray array(JsonObject o, String name) {
        if (!o.has(name) || !o.get(name).isJsonArray()) throw new IllegalArgumentException("falta a lista " + name);
        JsonArray a = o.getAsJsonArray(name);
        if (a.size() > MAX_ENTRIES) throw new IllegalArgumentException(name + " passa de " + MAX_ENTRIES + " itens");
        return a;
    }

    private static double number(JsonObject o, String name, double fallback, double min, double max) {
        double v = o.has(name) ? o.get(name).getAsDouble() : fallback;
        // A comparação negada também rejeita NaN.
        if (!(v >= min && v <= max)) {
            throw new IllegalArgumentException(name + " deve estar entre " + min + " e " + max + " (veio " + v + ")");
        }
        return v;
    }

    private static void vector(JsonObject o, String name, double[] out, double maxDistance) {
        JsonArray a = array(o, name);
        if (a.size() != 3) throw new IllegalArgumentException(name + " precisa de [x, y, z]");
        double sum = 0;
        for (int i = 0; i < 3; i++) {
            out[i] = a.get(i).getAsDouble();
            sum += out[i] * out[i];
        }
        if (!(sum <= maxDistance * maxDistance)) {
            throw new IllegalArgumentException(name + " passa de " + maxDistance + " blocos da âncora");
        }
    }

    private static void optionalVector(JsonObject o, String name, double[] out) {
        if (o.has(name)) vector(o, name, out, MAX_CAMERA_DISTANCE);
    }

    private static String string(JsonObject o, String name, String fallback) {
        String v = o.has(name) ? o.get(name).getAsString() : fallback;
        if (v.length() > MAX_TEXT) throw new IllegalArgumentException(name + " passa de " + MAX_TEXT + " caracteres");
        return v;
    }

    private static boolean bool(JsonObject o, String name, boolean fallback) {
        return o.has(name) ? o.get(name).getAsBoolean() : fallback;
    }
}
