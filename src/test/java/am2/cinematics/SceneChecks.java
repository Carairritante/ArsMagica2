package am2.cinematics;

import am2.cinematics.ritual.Minigame;
import am2.cinematics.ritual.OpenArea;
import am2.cinematics.scene.Clearance;
import am2.cinematics.scene.Pose;
import am2.cinematics.scene.Scene;
import am2.cinematics.scene.VeinMap;
import am2.cinematics.server.SceneStore;
import java.io.File;
import java.nio.file.Files;

/** Verificação sem Minecraft: cenas incluídas, interpolação e rejeição de entradas ruins. */
public final class SceneChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        File[] files = new File(args[0]).listFiles();
        require(files != null && files.length >= 2, "cenas de exemplo ausentes");
        for (File file : files) {
            Scene scene = Scene.parse(new String(Files.readAllBytes(file.toPath()), Scene.UTF8));
            Scene.Sample s = new Scene.Sample();
            for (double t = 0; t <= scene.duration; t += 0.25) {
                scene.sample(t, s);
                double distance = Math.sqrt(s.pos[0] * s.pos[0] + s.pos[1] * s.pos[1] + s.pos[2] * s.pos[2]);
                require(!Double.isNaN(distance) && distance <= Scene.MAX_CAMERA_DISTANCE,
                        file.getName() + ": câmera fora do limite em t=" + t);
                require(s.fov >= 10 && s.fov <= 150, file.getName() + ": FOV inválido em t=" + t);
            }
            for (Scene.Key key : scene.keys) {
                scene.sample(key.t, s);
                require(close(s.pos, key.pos) && close(s.look, key.look), file.getName() + ": não passa pelo ponto t=" + key.t);
            }
            checkActors(file.getName(), scene);
            checkRunes(file.getName(), scene);
            if ("despertar.json".equals(file.getName())) checkAwakening(scene);
            boolean acts = "despertar.json".equals(file.getName()) || file.getName().startsWith("fusao");
            require(scene.actsOnBody() == acts, file.getName() + ": uso do dublê inesperado");
            if ("fusao.json".equals(file.getName())) {
                checkFusion(scene);
                checkGates(scene);
            }
            if ("fusao_falha.json".equals(file.getName())) checkFailure(scene);
        }
        checkMinigames();

        Scene cut = Scene.parse(scene("{'t':0,'pos':[0,1,0],'look':[0,1,5]},"
                + "{'t':20,'pos':[10,1,0],'look':[0,1,5],'cut':true}"));
        Scene.Sample s = new Scene.Sample();
        cut.sample(19.9, s);
        require(s.pos[0] == 0, "antes do corte a câmera deve ficar no plano anterior");
        cut.sample(20, s);
        require(s.pos[0] == 10, "no corte a câmera deve pular");

        Scene hold = Scene.parse(scene("{'t':0,'pos':[0,1,0],'look':[0,1,5]},"
                + "{'t':20,'pos':[10,1,0],'look':[0,1,5]},"
                + "{'t':40,'pos':[10,1,0],'look':[0,1,5]},"
                + "{'t':60,'pos':[20,1,0],'look':[0,1,5]}"));
        for (double t = 20; t <= 40; t += 0.5) {
            hold.sample(t, s);
            require(Math.abs(s.pos[0] - 10) < 1e-9, "pausa não pode derivar (t=" + t + ")");
        }

        require(cut.shotStart(19.9) == 0 && cut.shotEnd(19.9) == 19, "limites do primeiro plano");
        require(cut.shotStart(20) == 20 && cut.shotEnd(20) == 100, "limites do segundo plano");
        checkClearance();

        checkRotation();
        checkPose();
        checkVeinMap();
        checkOpenArea();
        checkStore();
        require(Scene.heartbeat(0) > 0.99 && Scene.heartbeat(Scene.HEARTBEAT_TICKS) > 0.99
                && Scene.heartbeat(11) < 0.1, "batida do coração");
        for (double t = 0; t < 40; t += 0.05) {
            require(Math.abs(Scene.heartbeat(t) - Scene.heartbeat(t + 0.05)) < 0.1, "batida deu um salto em t=" + t);
        }

        String actor = "{'duration':100,'camera':[{'t':0,'pos':[0,1,0],'look':[0,1,5]}],'actors':[%s]}";
        reject(String.format(actor, "{'type':'dragao','keys':[{'t':0,'pos':[0,1,0]}]}"));
        reject(String.format(actor, "{'type':'book','keys':[]}"));
        reject(String.format(actor, "{'type':'book','keys':[{'t':0}]}"));
        reject(String.format(actor, "{'type':'book','keys':[{'t':5,'pos':[0,1,0]},{'t':5}]}"));
        reject(String.format(actor, "{'type':'glow','color':'roxo','keys':[{'t':0,'pos':[0,1,0]}]}"));
        String particles = "{'duration':100,'camera':[{'t':0,'pos':[0,1,0],'look':[0,1,5]}],'events':[%s]}";
        reject(String.format(particles, "{'type':'particles','t':0,'duration':5,'name':'a b','pos':[0,1,0]}"));
        reject(String.format(particles, "{'type':'particles','t':0,'duration':5,'name':'flame','pos':[0,1,0],'count':999}"));
        reject(String.format(particles, "{'type':'particles','t':0,'duration':5,'name':'flame'}"));
        reject(String.format(particles, "{'type':'runes','t':0,'duration':10,'travel':20,'pos':[0,1,0],'to':[0,2,0]}"));
        reject(String.format(particles, "{'type':'runes','t':0,'duration':10,'travel':5,'pos':[0,1,0]}"));
        Scene.parse(String.format(actor, "{'type':'veins','keys':[{'t':0,'open':0},{'t':50,'open':1}]}").replace('\'', '"'));

        String ritual = "{'duration':100,'camera':[{'t':0,'pos':[0,1,0],'look':[0,1,5]}],'actors':[%s]}";
        reject(String.format(ritual, "{'type':'chalk','keys':[{'t':0,'pos':[0,0,0]}]}"));
        reject(String.format(ritual, "{'type':'chalk','points':[[99,0]],'keys':[{'t':0,'pos':[0,0,0]}]}"));
        reject(String.format(ritual, "{'type':'item','item':'espada','keys':[{'t':0,'pos':[0,1,0]}]}"));
        Scene.parse(String.format(ritual, "{'type':'night','keys':[{'t':0,'alpha':0},{'t':9,'alpha':1}]}").replace('\'', '"'));

        reject("");
        reject("[]");
        reject("{'duration':100}");
        reject(scene(""));
        reject(scene("{'t':5,'pos':[0,1,0],'look':[0,1,5]}"));
        reject(scene("{'t':0,'pos':[500,1,0],'look':[0,1,5]}"));
        reject(scene("{'t':0,'pos':[NaN,1,0],'look':[0,1,5]}"));
        reject(scene("{'t':0,'pos':[0,1],'look':[0,1,5]}"));
        reject(scene("{'t':0,'pos':[0,1,0],'look':[0,1,5],'fov':999}"));
        reject(scene("{'t':0,'pos':[0,1,0],'look':[0,1,5]},{'t':0,'pos':[0,1,0],'look':[0,1,5]}"));
        reject("{\"duration\":999999,\"camera\":[{\"t\":0,\"pos\":[0,1,0],\"look\":[0,1,5]}]}");
        StringBuilder huge = new StringBuilder(scene("{'t':0,'pos':[0,1,0],'look':[0,1,5]}"));
        while (huge.length() <= Scene.MAX_BYTES) huge.append("          ");
        reject(huge.toString());

        System.out.println("SceneChecks: " + checks + " verificações OK em " + files.length + " cenas");
    }

    /** Um obstáculo entre os ticks 40 e 45, exigindo metade do recuo. */
    private static void checkClearance() {
        Clearance.Needed wall = new Clearance.Needed() {
            @Override
            public double at(int tick) {
                return tick >= 40 && tick <= 45 ? 0.5 : 0;
            }
        };
        double previous = 0;
        for (double t = 0; t <= 100; t += 0.05) {
            double pull = Clearance.pull(wall, t, 0, 100);
            require(pull >= 0 && pull <= 0.5, "recuo fora do intervalo em t=" + t);
            require(Math.abs(pull - previous) < 0.01, "recuo deu um salto em t=" + t);
            if (t >= 40 && t <= 45) require(pull > 0.49, "câmera entraria no bloco em t=" + t);
            if (t < 40 - Clearance.WINDOW || t > 45 + Clearance.WINDOW) require(pull == 0, "recuo sem obstáculo em t=" + t);
            previous = pull;
        }
        require(Clearance.pull(wall, 35, 0, 39) == 0, "um plano não pode antecipar obstáculo do próximo");
    }

    private static void checkActors(String name, Scene scene) {
        Scene.ActorKey out = new Scene.ActorKey();
        for (Scene.Actor actor : scene.actors) {
            int first = actor.keys.get(0).t, last = actor.keys.get(actor.keys.size() - 1).t;
            require(!actor.sample(first - 0.1, out) && !actor.sample(last + 0.1, out), name + ": ator fora do intervalo");
            for (Scene.ActorKey key : actor.keys) {
                require(actor.sample(key.t, out) && (key.attach != Scene.Attach.NONE || close(out.pos, key.pos))
                        && Math.abs(out.alpha - key.alpha) < 1e-9
                        && Math.abs(out.open - key.open) < 1e-9, name + ": ator não passa pelo ponto t=" + key.t);
            }
        }
    }

    /** Enquadramento e gestos do "Despertar". */
    private static void checkAwakening(Scene scene) {
        require(scene.align && scene.hideGear && !scene.pose.isEmpty(), "a cena precisa virar, esconder o equipamento e posar");
        Scene.Actor book = null, orb = null;
        for (Scene.Actor actor : scene.actors) {
            if (actor.type == Scene.ActorType.BOOK) book = actor;
            if (actor.type == Scene.ActorType.ORB) orb = actor;
        }
        require(book != null && orb != null, "cena sem livro ou sem esfera");
        Scene.Sample camera = new Scene.Sample();
        Scene.ActorKey key = new Scene.ActorKey();
        Pose.Key pose = new Pose.Key();
        double[] left = new double[3], right = new double[3];
        double[] head = { 0, 1.7, 0 }, heart = { -0.08, 1.2, 0.05 };

        for (double t = 0; t <= scene.duration; t += 0.25) {
            scene.sample(t, camera);
            boolean insideBody = Math.abs(camera.pos[0]) < 0.45 && Math.abs(camera.pos[2]) < 0.45 && camera.pos[1] < 1.95;
            require(!insideBody, "câmera dentro do jogador em t=" + t);
            Pose.sample(scene.pose, t, pose);
            Pose.hand(pose, false, left);
            Pose.hand(pose, true, right);
            require(!insideTorso(left) && !insideTorso(right), "mão atravessa o tronco em t=" + t);
            require(book.sample(t, key), "o livro sumiu em t=" + t);
            if (t <= 20 || t >= 156) {
                require(distance(key.pos, right) < 0.2, "livro fora da mão direita em t=" + t);
            }
            // No close do rosto (79 a 108) o livro pode ficar fora de quadro.
            if (t <= 78 || t >= 109 && t <= 156) require(onScreen(camera, key.pos), "livro fora do quadro em t=" + t);
            if (t >= 33 && t <= 140) require(!hitsBody(camera.pos, key.pos), "jogador tapa o livro em t=" + t);
            if (t >= 48 && t <= 130) {
                require(onScreen(camera, head), "cabeça fora do quadro em t=" + t);
                require(pose.head < -40, "a cabeça deve ficar erguida ao receber o conhecimento, t=" + t);
                require(distance(key.pos, right) > 0.6, "o livro deveria estar flutuando em t=" + t);
            }
            if (t >= 172 && t <= 214) require(onScreen(camera, left), "mão esquerda fora do quadro em t=" + t);
            if (t >= 170 && t <= 206) {
                require(orb.sample(t, key) && distance(key.pos, left) < 0.2, "esfera fora da mão em t=" + t);
            }
            // A mão precisa estar no peito quando a esfera entra.
            if (t >= 216 && t <= 232) require(distance(left, heart) < 0.24, "mão longe do peito em t=" + t);
            if (t >= 216 && t <= 272) require(onScreen(camera, heart), "coração fora do quadro em t=" + t);
        }
        Pose.sample(scene.pose, 44, pose);
        require(Math.abs(pose.head) < 5, "antes do tranco a cabeça está reta");
        Pose.sample(scene.pose, scene.duration, pose);
        require(pose.weight == 0, "a pose precisa terminar na animação normal");
    }

    /** Ritual: altar recriado, reagentes visíveis, eclipse, flutuação e volta ao normal. */
    private static void checkFusion(Scene scene) {
        require(scene.hideGear && !scene.align && scene.stand != null && scene.stand[1] == 1,
                "o conjurador fica em cima da esmeralda");
        Scene.Sample camera = new Scene.Sample();
        Scene.ActorKey key = new Scene.ActorKey();
        Pose.Key pose = new Pose.Key();
        double[] heart = new double[3];
        java.util.Set<String> reagents = new java.util.HashSet<String>();
        for (Scene.Actor actor : scene.actors) {
            if (actor.type == Scene.ActorType.CHALK) {
                require(actor.points.size() == 20, "o círculo do ritual tem 20 runas de giz");
                for (double[] p : actor.points) {
                    // O desenho é simétrico: qualquer giro de 90 graus cai nos mesmos blocos.
                    require(contains(actor, -p[1], p[0]), "giz assimétrico em " + p[0] + "," + p[1]);
                }
                require(actor.sample(100, key) && key.open == 1, "giz não acendeu");
            } else if (actor.type == Scene.ActorType.CANDLES) {
                require(actor.points.size() == 4 && actor.sample(66, key) && key.open == 1, "velas não acenderam");
            } else if (actor.type == Scene.ActorType.NIGHT) {
                require(actor.sample(150, key) && key.alpha == 1, "deveria ser noite no meio da cena");
                // Fora do último ponto o ator não existe, o que também significa dia.
                require(!actor.sample(scene.duration - 2, key) || key.alpha == 0, "o dia precisa voltar");
            } else if (actor.type == Scene.ActorType.ITEM) {
                reagents.add(actor.item + "@" + actor.meta);
                int rise = actor.keys.get(0).t;
                for (double t = rise + 6; t <= rise + 16; t += 1) {
                    scene.sample(t, camera);
                    require(actor.sample(t, key) && onScreen(camera, key.pos), actor.item + " fora do quadro em t=" + t);
                }
                if (actor.keys.get(actor.keys.size() - 1).attach == Scene.Attach.HEART) {
                    int end = actor.keys.get(actor.keys.size() - 1).t;
                    Pose.sample(scene.pose, end, pose);
                    Pose.heart(pose, heart);
                    heart[1] += scene.stand[1];
                    require(actor.sample(end, key) && distance(key.pos, heart) < 0.01, actor.item + " não entra no coração");
                }
            } else if (actor.type == Scene.ActorType.RAIN) {
                require(actor.sample(210, key) && key.alpha == 1, "a tempestade precisa estar formada");
                require(!actor.sample(scene.duration - 2, key) || key.alpha == 0, "a chuva precisa parar");
            }
        }
        checkStorm(scene);
        // 8 originais, três essências diferentes e o Orbe.
        require(reagents.size() == 12, "o ritual mostra 12 tipos de reagente, há " + reagents.size());
        checkOffering(scene);

        double highest = 0;
        for (double t = 0; t <= scene.duration; t += 0.25) {
            scene.sample(t, camera);
            Pose.sample(scene.pose, t, pose);
            highest = Math.max(highest, pose.lift);
            double feet = scene.stand[1] + pose.lift;
            boolean inBody = Math.abs(camera.pos[0]) < 0.5 && Math.abs(camera.pos[2]) < 0.5
                    && camera.pos[1] < feet + 1.95 && camera.pos[1] > feet - 1.2;
            require(!inBody, "câmera dentro do conjurador ou da esmeralda em t=" + t);
            Pose.heart(pose, heart);
            heart[1] += scene.stand[1];
            // Do início ao título o conjurador (e o centro das veias) continua em quadro.
            if (t <= 540) require(onScreen(camera, heart), "conjurador fora do quadro em t=" + t);
        }
        checkWorld(scene);
        require(highest > 1.2, "o conjurador precisa flutuar");
        Pose.sample(scene.pose, scene.duration, pose);
        require(pose.weight == 0 && pose.lift == 0, "termina no chão e na animação normal");
    }

    /** As quantidades do ritual novo: 4 essências base (nas velas), 2 superiores, 1 pura e o Orbe. */
    private static void checkOffering(Scene scene) {
        int base = 0, high = 0, pure = 0, orbs = 0, orbArrival = 0, othersArrival = 0;
        java.util.Set<String> candles = new java.util.HashSet<String>();
        for (Scene.Actor actor : scene.actors) {
            if (actor.type != Scene.ActorType.ITEM) continue;
            Scene.ActorKey last = actor.keys.get(actor.keys.size() - 1);
            boolean essence = "arsmagica2:essence".equals(actor.item);
            if (essence && actor.meta == 12) {
                base++;
                double[] p = last.pos;
                require(Math.abs(Math.hypot(p[0], p[2]) - 2) < 1e-6 && (p[0] == 0 || p[2] == 0),
                        "essência base deve terminar numa vela");
                candles.add((int) p[0] + "," + (int) p[2]);
            } else if (essence && actor.meta == 11) {
                high++;
                require(last.attach == Scene.Attach.RIGHT_HAND || last.attach == Scene.Attach.LEFT_HAND,
                        "essência superior termina numa mão");
            } else if (essence && actor.meta == 10) {
                pure++;
            }
            boolean orb = "arsmagica2:rune".equals(actor.item) && actor.meta == 19;
            if (orb) orbs++;
            if (last.attach == Scene.Attach.HEART) {
                // Chegada: o primeiro ponto preso ao coração.
                for (Scene.ActorKey k : actor.keys) {
                    if (k.attach != Scene.Attach.HEART) continue;
                    if (orb) orbArrival = k.t;
                    else othersArrival = Math.max(othersArrival, k.t);
                    break;
                }
            }
        }
        require(base == 4 && candles.size() == 4, "quatro essências base, uma por vela");
        require(high == 2 && pure == 1 && orbs == 1, "duas essências superiores, uma pura e um Orbe");
        require(orbArrival > othersArrival && othersArrival > 0, "o Orbe entra no peito por último");
        // Durante o minigame dos raios o Orbe paira em destaque, dentro do quadro.
        Scene.Sample camera = new Scene.Sample();
        Scene.ActorKey key = new Scene.ActorKey();
        int storm = scene.gates.get(1).t;
        scene.sample(storm, camera);
        for (Scene.Actor actor : scene.actors) {
            if ("arsmagica2:rune".equals(actor.item)) {
                require(actor.sample(storm, key) && key.alpha == 1 && key.pos[1] > 4 && onScreen(camera, key.pos),
                        "o Orbe deve estar em destaque na tempestade");
                // Do momento em que paira até entrar no peito, não pode sair do quadro.
                for (double t = 196; t <= 266; t += 0.25) {
                    scene.sample(t, camera);
                    require(actor.sample(t, key) && onScreen(camera, key.pos), "o Orbe saiu do quadro em t=" + t);
                }
            }
        }
    }

    /** As três pausas caem em momentos calmos, com o que o minigame mostra já em cena. */
    private static void checkGates(Scene scene) {
        require(scene.gates.size() == 3 && scene.ritualGates() == scene.gates, "o ritual tem três pausas");
        Minigame.Type[] order = { Minigame.Type.CANDLES, Minigame.Type.LIGHTNING, Minigame.Type.PULSE };
        Scene.Sample camera = new Scene.Sample();
        Scene.ActorKey key = new Scene.ActorKey();
        for (int i = 0; i < order.length; i++) {
            Scene.Gate gate = scene.gates.get(i);
            require(gate.game == order[i], "ordem dos minigames");
            for (Scene.Event e : scene.events) {
                if (e.type != Scene.Type.FADE && e.type != Scene.Type.TITLE && e.type != Scene.Type.SHAKE) continue;
                require(gate.t < e.t || gate.t >= e.t + e.duration,
                        "pausa em t=" + gate.t + " congelaria " + e.type);
            }
            scene.sample(gate.t, camera);
            for (Scene.Actor actor : scene.actors) {
                if (gate.game == Minigame.Type.CANDLES && actor.type == Scene.ActorType.CANDLES) {
                    require(actor.sample(gate.t, key) && key.open == 1, "velas apagadas no minigame");
                    for (double[] p : actor.points) {
                        require(onScreen(camera, new double[] { p[0], 0.47, p[1] }),
                                "vela fora do quadro no minigame: " + p[0] + "," + p[1]);
                    }
                } else if (gate.game == Minigame.Type.LIGHTNING && actor.type == Scene.ActorType.RAIN) {
                    require(actor.sample(gate.t, key) && key.alpha == 1, "sem tempestade no minigame dos raios");
                } else if (gate.game == Minigame.Type.PULSE && actor.type == Scene.ActorType.WORLDVEINS) {
                    require(actor.sample(gate.t, key) && key.open > 0.999 && key.pulse < 0,
                            "o pulso do mundo vem antes de as veias voltarem");
                }
            }
        }
        require(Scene.parse(scene("{'t':0,'pos':[0,1,0],'look':[0,1,5]}")).ritualGates().size() == 3,
                "uma cena sem pausas ainda pede os minigames");
    }

    /** A rejeição: curta, sem pausas, sem câmera dentro do corpo e devolvendo a pose. */
    private static void checkFailure(Scene scene) {
        require(scene.duration <= 140 && scene.gates.isEmpty() && scene.stand != null && scene.stand[1] == 1,
                "a cena de falha é curta e acontece sobre a esmeralda");
        Scene.Sample camera = new Scene.Sample();
        Pose.Key pose = new Pose.Key();
        double[] heart = new double[3];
        for (double t = 0; t <= scene.duration; t += 0.25) {
            scene.sample(t, camera);
            Pose.sample(scene.pose, t, pose);
            double feet = scene.stand[1] + pose.lift;
            boolean inBody = Math.abs(camera.pos[0]) < 0.5 && Math.abs(camera.pos[2]) < 0.5
                    && camera.pos[1] < feet + 1.95 && camera.pos[1] > feet - 1.2;
            require(!inBody, "falha: câmera dentro do conjurador em t=" + t);
            Pose.heart(pose, heart);
            heart[1] += scene.stand[1];
            require(onScreen(camera, heart), "falha: conjurador fora do quadro em t=" + t);
        }
        Pose.sample(scene.pose, scene.duration, pose);
        require(pose.weight == 0 && pose.lift == 0, "falha: termina no chão");
        for (Scene.Actor actor : scene.actors) {
            require(actor.type != Scene.ActorType.CHALK && actor.type != Scene.ActorType.CANDLES,
                    "o círculo de verdade volta na falha; não desenhar outro por cima");
        }
    }

    /** Acerta cada batida no tick certo (ou a sequência das velas, uma tecla a cada 5 ticks). */
    private static Minigame perfect(Minigame game) {
        for (int i = 0; i < game.count(); i++) {
            boolean candles = game.type == Minigame.Type.CANDLES;
            game.press(candles ? game.note(i) : Minigame.ACTION, candles ? 10 + i * 5 : game.note(i));
        }
        return game;
    }

    private static void checkMinigames() {
        for (Minigame.Type type : Minigame.Type.values()) {
            for (long seed = 0; seed < 500; seed++) {
                Minigame game = new Minigame(type, seed), copy = new Minigame(type, seed);
                for (int i = 0; i < game.count(); i++) {
                    require(game.note(i) == copy.note(i), "mesma semente, mesma partida");
                    if (type == Minigame.Type.CANDLES) {
                        require(game.note(i) >= 0 && game.note(i) <= 3
                                && (i == 0 || game.note(i) != game.note(i - 1)), "sequência das velas");
                    } else {
                        require(game.note(i) > game.window()
                                && game.note(i) + game.window() <= Minigame.PLAY_TICKS
                                && (i == 0 || game.note(i) - game.note(i - 1) > 2 * game.window()),
                                type + ": batida fora do tempo, semente " + seed);
                    }
                }
                require(perfect(game).state() == Minigame.State.PASSED, type + ": jogar certo precisa passar");
                require(copy.advance(Minigame.PLAY_TICKS) != null && copy.advance(Minigame.PLAY_TICKS + 1)
                        == Minigame.State.FAILED, type + ": sem jogar precisa falhar");
                Minigame spam = new Minigame(type, seed);
                for (int tick = 0; tick <= Minigame.PLAY_TICKS; tick++) {
                    for (int key = Minigame.UP; key <= Minigame.ACTION; key++) {
                        if (type != Minigame.Type.CANDLES) spam.press(key, tick);
                    }
                }
                if (type != Minigame.Type.CANDLES) {
                    require(spam.state() == Minigame.State.FAILED, type + ": martelar a tecla não pode passar");
                }
            }
        }
        Minigame candles = new Minigame(Minigame.Type.CANDLES, 7);
        for (int i = 0; i < 3; i++) {
            // A demonstração começa no tick 20, um passo a cada 14.
            int tick = 20 + i * 14;
            require(candles.demo(tick) == candles.note(i), "demonstração das velas, passo " + i);
        }
        require(candles.demo(candles.lead()) == -1, "a demonstração acaba antes de valer o tempo");
        int wrong = (candles.note(1) + 1) % 4;
        require(candles.press(candles.note(0), 1) == Minigame.HIT && candles.press(wrong, 2) == Minigame.MISS
                && candles.progress() == 0 && candles.state() == Minigame.State.RUNNING, "errar recomeça a sequência");
        require(candles.press(Minigame.ACTION, 3) == Minigame.PENDING, "outras teclas não contam");
        require(perfect(candles).state() == Minigame.State.PASSED, "dá para recomeçar depois do erro");

        // Raios: 2 de 3. Pulso: 3 de 4.
        for (Minigame.Type type : new Minigame.Type[] { Minigame.Type.LIGHTNING, Minigame.Type.PULSE }) {
            Minigame game = new Minigame(type, 3);
            for (int i = 1; i < game.count(); i++) game.press(Minigame.ACTION, game.note(i) + game.window());
            require(game.state() == Minigame.State.PASSED, type + ": pode perder uma batida, no limite da folga");
            Minigame twice = new Minigame(type, 3);
            for (int i = 2; i < twice.count(); i++) twice.press(Minigame.ACTION, twice.note(i));
            require(twice.state() == Minigame.State.FAILED, type + ": perder duas falha");
            Minigame late = new Minigame(type, 3);
            require(late.press(Minigame.ACTION, late.note(0) + late.window() + 1) == Minigame.PENDING
                    && late.judged(0) == Minigame.MISS, type + ": atrasado é erro");
        }
    }

    /** Veias no terreno, o pulso que volta e o clímax. */
    private static void checkWorld(Scene scene) {
        require("minecraft:emerald_block".equals(scene.altar), "o comando precisa achar a esmeralda");
        Scene.Actor veins = null, body = null, pillar = null, wave = null;
        for (Scene.Actor actor : scene.actors) {
            if (actor.type == Scene.ActorType.WORLDVEINS) veins = actor;
            if (actor.type == Scene.ActorType.VEINS) body = actor;
            if (actor.type == Scene.ActorType.PILLAR) pillar = actor;
            if (actor.type == Scene.ActorType.WAVE) wave = actor;
        }
        require(veins != null && body != null && pillar != null && wave != null, "faltam atores do clímax");
        Scene.ActorKey key = new Scene.ActorKey();
        int start = veins.keys.get(0).t;
        require(body.sample(start, key) && key.open > 0.9, "a mana precisa descer pelo corpo antes de sair para o chão");
        double open = 0, pulse = 0, farthest = 0;
        int arrival = -1;
        for (double t = start; t <= veins.keys.get(veins.keys.size() - 1).t; t += 0.25) {
            veins.sample(t, key);
            require(key.open >= open - 1e-9, "as veias não podem encolher, t=" + t);
            open = key.open;
            farthest = Math.max(farthest, key.scale);
            if (key.pulse >= 0) {
                require(open > 0.999 && key.pulse >= pulse - 1e-9, "o pulso só volta, depois de tudo espalhado, t=" + t);
                pulse = key.pulse;
                if (pulse > 0.999 && arrival < 0) arrival = (int) t;
            }
        }
        require(farthest >= 100 && open > 0.999 && arrival > 0, "as veias precisam ir muito longe e voltar");
        require(pillar.keys.get(0).t >= arrival && wave.keys.get(0).t >= arrival,
                "a coluna e a onda vêm depois que o pulso chega");
        Scene.Sample camera = new Scene.Sample();
        scene.sample(420, camera);
        require(camera.pos[1] > 90 && scene.looksFromAbove(), "a câmera precisa subir muito para mostrar o alcance");
        // No topo, boa parte do desenho aparece: pontos a 60 blocos nas quatro direções.
        for (int i = 0; i < 4; i++) {
            double[] far = { 60 * Math.cos(i * Math.PI / 2), 0, 60 * Math.sin(i * Math.PI / 2) };
            require(onScreen(camera, far) || i == 3 || i == 1, "veias distantes fora do quadro: " + i);
        }
    }

    /** Raios: vários, alguns longe, e os do plano do céu aparecem na tela. */
    private static void checkStorm(Scene scene) {
        int strikes = 0, framed = 0;
        double farthest = 0;
        Scene.Sample camera = new Scene.Sample();
        for (Scene.Event e : scene.events) {
            if (e.type != Scene.Type.LIGHTNING) continue;
            strikes++;
            double distance = Math.hypot(e.pos[0], e.pos[2]);
            farthest = Math.max(farthest, distance);
            require(distance > 3, "raio em cima do conjurador em t=" + e.t);
            if (e.t >= 171 && e.t <= 228) {
                scene.sample(e.t, camera);
                if (onScreen(camera, new double[] { e.pos[0], 5, e.pos[2] })) framed++;
            }
        }
        require(strikes >= 10 && farthest > 60, "poucos raios: " + strikes);
        require(framed >= 3, "os raios do começo da tempestade precisam aparecer: " + framed);
    }

    /** Terreno plano de teste: chão em y=63 (altar em y=64), com blocos trocados à vontade. */
    private static final class Flat implements OpenArea.Ground {
        static final int AIR = 0, SOIL = 1, STONE = 2, GLASS = 3, WATER = 4;
        /** Colunas de terra; as demais são de pedra. */
        int soilColumns = Integer.MAX_VALUE;
        /** Altura de um teto, ou -1; {@code roof} é o bloco dele. */
        int roofY = -1, roof = AIR;
        private final java.util.Map<String, Boolean> columns = new java.util.HashMap<String, Boolean>();

        private int block(int x, int y, int z) {
            if (y == roofY) return roof;
            if (y > 63) return AIR;
            if (y < 63) return STONE;
            String key = x + "," + z;
            // O orçamento de terra é gasto nas colunas consultadas primeiro.
            if (!columns.containsKey(key)) columns.put(key, columns.size() < soilColumns);
            return columns.get(key) ? SOIL : STONE;
        }

        public int top(int x, int z) {
            return roof == STONE ? roofY + 1 : 64;
        }

        public boolean isSoil(int x, int y, int z) {
            return block(x, y, z) == SOIL;
        }

        public boolean isOpaque(int x, int y, int z) {
            int b = block(x, y, z);
            return b == SOIL || b == STONE;
        }

        public boolean isFree(int x, int y, int z) {
            return block(x, y, z) == AIR;
        }
    }

    private static int openColumns(Flat ground) {
        return OpenArea.count(ground, 0, 64, 0);
    }

    private static void checkOpenArea() {
        int open = openColumns(new Flat());
        require(open > 150 && open < 225, "gramado aberto: " + open + " colunas");
        Flat stone = new Flat();
        stone.soilColumns = 0;
        require(openColumns(stone) == 0, "chão de pedra não conta");
        for (int limit : new int[] { OpenArea.NEEDED - 1, OpenArea.NEEDED }) {
            Flat some = new Flat();
            some.soilColumns = limit;
            require(openColumns(some) == limit, "contagem exata de " + limit + " colunas");
        }
        Flat roofed = new Flat();
        roofed.roofY = 66;
        roofed.roof = Flat.STONE;
        // Como no Extra Utilities, as 8 colunas da borda passam por fora da esfera na altura do teto.
        require(openColumns(roofed) == 8, "teto opaco esconde a terra: " + openColumns(roofed));
        Flat glass = new Flat();
        glass.roofY = 66;
        glass.roof = Flat.GLASS;
        require(openColumns(glass) == open, "bloco não opaco acima não esconde a terra");
        Flat water = new Flat();
        water.roofY = 64;
        water.roof = Flat.WATER;
        require(openColumns(water) == 0, "terra coberta por água não conta");
    }

    private static void checkVeinMap() {
        java.util.List<VeinMap.Segment> a = VeinMap.generate(23), b = VeinMap.generate(23);
        require(a.size() == b.size() && a.size() > 1500 && a.size() < 9000, "quantidade de trechos: " + a.size());
        double longest = 0;
        double[] reach = new double[8];
        for (int i = 0; i < a.size(); i++) {
            VeinMap.Segment s = a.get(i);
            require(s.x1 == b.get(i).x1 && s.z2 == b.get(i).z2, "o desenho precisa ser igual para todos");
            require(s.from >= 0 && s.to > s.from && s.to <= 1 && s.width > 0 && s.width <= 1, "trecho inválido " + i);
            require(Math.hypot(s.x2, s.z2) <= 1.0001, "veia fora do disco");
            longest = Math.max(longest, s.to);
            int sector = (int) ((Math.atan2(s.z2, s.x2) + Math.PI) / (Math.PI / 4)) % 8;
            reach[sector] = Math.max(reach[sector], Math.hypot(s.x2, s.z2));
        }
        require(longest > 0.999, "distância normalizada");
        for (int i = 0; i < 8; i++) require(reach[i] > 0.7, "setor " + i + " mal coberto: " + reach[i]);
        System.out.println("VeinMap: " + a.size() + " trechos");
    }

    private static boolean contains(Scene.Actor actor, double x, double z) {
        for (double[] p : actor.points) {
            if (p[0] == x && p[1] == z) return true;
        }
        return false;
    }

    private static boolean insideTorso(double[] hand) {
        return Math.abs(hand[0]) < 0.2 && hand[1] > 0.75 && hand[1] < 1.38 && Math.abs(hand[2]) < 0.1;
    }

    /** Visível em tela 16:9, dentro das barras pretas e com folga nas bordas. */
    private static boolean onScreen(Scene.Sample camera, double[] target) {
        double[] forward = new double[3], to = new double[3];
        for (int i = 0; i < 3; i++) {
            forward[i] = camera.look[i] - camera.pos[i];
            to[i] = target[i] - camera.pos[i];
        }
        normalize(forward);
        double[] right = { -forward[2], 0, forward[0] };
        normalize(right);
        double[] up = { right[1] * forward[2] - right[2] * forward[1], right[2] * forward[0] - right[0] * forward[2],
                right[0] * forward[1] - right[1] * forward[0] };
        double depth = dot(to, forward), half = Math.tan(Math.toRadians(camera.fov) / 2);
        return depth > 0 && Math.abs(dot(to, up) / depth) <= half * 0.72
                && Math.abs(dot(to, right) / depth) <= half * 16 / 9 * 0.85;
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static void normalize(double[] v) {
        double length = Math.sqrt(dot(v, v));
        for (int i = 0; i < 3; i++) v[i] /= length;
    }

    private static double distance(double[] a, double[] b) {
        return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
    }

    private static void checkRunes(String name, Scene scene) {
        double[] pos = new double[3];
        for (Scene.Event e : scene.events) {
            if (e.type != Scene.Type.RUNES) continue;
            for (int i = 0; i < e.count; i++) {
                double stagger = e.count < 2 ? 0 : (double) (e.duration - e.travel) / (e.count - 1);
                double start = e.t + i * stagger, end = start + e.travel;
                require(end <= e.t + e.duration + 1e-9, name + ": runa chega depois do fim do evento");
                require(Scene.rune(e, i, start - 0.1, pos) < 0, name + ": runa antes da hora");
                require(Scene.rune(e, i, start + 0.001, pos) >= 0, name + ": runa não saiu");
                for (int axis = 0; axis < 3; axis++) {
                    require(Math.abs(pos[axis] - e.pos[axis]) <= e.spread[axis] + 0.01, name + ": runa não sai da origem");
                }
                require(Scene.rune(e, i, end - 1e-6, pos) > 0.99 && close(round(pos), round(e.target)),
                        name + ": runa não chega ao destino");
            }
        }
    }

    private static double[] round(double[] v) {
        return new double[] { Math.rint(v[0] * 1e3), Math.rint(v[1] * 1e3), Math.rint(v[2] * 1e3) };
    }

    /** O segmento entre a câmera e o alvo atravessa a caixa do jogador? */
    private static boolean hitsBody(double[] from, double[] to) {
        for (double u = 0; u <= 1; u += 0.005) {
            double x = from[0] + (to[0] - from[0]) * u;
            double y = from[1] + (to[1] - from[1]) * u;
            double z = from[2] + (to[2] - from[2]) * u;
            if (Math.abs(x) < 0.45 && y < 1.95 && Math.abs(z) < 0.25) return true;
        }
        return false;
    }

    /** Cenas incluídas: atualizam sozinhas, mas edições do usuário são preservadas. */
    private static void checkStore() throws Exception {
        File config = Files.createTempDirectory("amcinematics").toFile();
        SceneStore.init(config);
        File folder = new File(config, "amcinematics/scenes");
        File demo = new File(folder, "demo.json"), orbit = new File(folder, "orbita.json");
        File awakening = new File(folder, "despertar.json");
        require(demo.isFile() && orbit.isFile() && awakening.isFile(), "cenas não foram instaladas");
        byte[] bundled = Files.readAllBytes(awakening.toPath());

        byte[] edited = "{}".getBytes(Scene.UTF8);
        Files.write(demo.toPath(), edited);
        require(orbit.delete(), "não apagou a cena");
        // Simula uma versão anterior do mod que instalou outro conteúdo, sem edição do usuário.
        byte[] older = "versao antiga".getBytes(Scene.UTF8);
        Files.write(awakening.toPath(), older);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(older);
        File record = new File(config, "amcinematics/instaladas.properties");
        java.util.Properties installed = new java.util.Properties();
        installed.load(new java.io.ByteArrayInputStream(Files.readAllBytes(record.toPath())));
        installed.setProperty("despertar", older.length + "-" + Long.toHexString(crc.getValue()));
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        installed.store(buffer, null);
        Files.write(record.toPath(), buffer.toByteArray());

        SceneStore.init(config);
        require(java.util.Arrays.equals(Files.readAllBytes(demo.toPath()), edited), "edição do usuário foi sobrescrita");
        require(orbit.isFile(), "cena apagada não voltou");
        require(java.util.Arrays.equals(Files.readAllBytes(awakening.toPath()), bundled), "cena antiga não foi atualizada");
        for (File f : folder.listFiles()) f.delete();
        folder.delete();
        record.delete();
        record.getParentFile().delete();
        config.delete();
    }

    private static void checkPose() {
        double[] hand = new double[3];
        Pose.Key key = new Pose.Key();
        key.weight = 1;
        // Braço direito esticado para a frente: palma na altura do ombro, à frente.
        key.right[Pose.X] = -90;
        Pose.hand(key, true, hand);
        double[] expected = new double[3];
        Pose.toLocal(-6, 2, -9.5, expected);
        require(close(hand, expected), "braço para a frente");
        // Só o cotovelo a 90 graus: antebraço horizontal saindo do cotovelo.
        key.right[Pose.X] = 0;
        key.right[Pose.ELBOW] = -90;
        Pose.hand(key, true, hand);
        Pose.toLocal(-6, 6, -5.5, expected);
        require(close(hand, expected), "cotovelo dobrado");
        reject("{'duration':100,'camera':[{'t':0,'pos':[0,1,0],'look':[0,1,5]}],'pose':[{'t':0,'right':[0,0,0]}]}");
        reject("{'duration':100,'camera':[{'t':0,'pos':[0,1,0],'look':[0,1,5]}],'pose':[{'t':5},{'t':5}]}");
        reject("{'duration':100,'camera':[{'t':0,'pos':[0,1,0],'look':[0,1,5]}],'actors':[{'type':'orb','keys':[{'t':0,'attach':'pe'}]}]}");
    }

    private static void checkRotation() {
        double[] out = new double[3];
        // Virado para o sul (yaw 0): frente é +Z e a direita é -X.
        Scene.rotate(new double[] { 1, 2, 3 }, 0, out);
        require(close(out, new double[] { -1, 2, 3 }), "rotação com yaw 0");
        // Virado para o oeste (yaw 90): frente é -X e a direita é -Z.
        Scene.rotate(new double[] { 1, 2, 3 }, 90, out);
        require(close(out, new double[] { -3, 2, -1 }), "rotação com yaw 90");
    }

    private static String scene(String keys) {
        return ("{'duration':100,'camera':[" + keys + "]}").replace('\'', '"');
    }

    private static void reject(String json) {
        try {
            Scene.parse(json.replace('\'', '"'));
        } catch (IllegalArgumentException expected) {
            checks++;
            return;
        }
        throw new AssertionError("deveria ter recusado: " + json);
    }

    private static boolean close(double[] a, double[] b) {
        return Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]) < 1e-6;
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
