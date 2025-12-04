import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public class ScriptMuxer {

    static class Script {
        String name;
        Process process;
        BufferedWriter writer;

        Script(String name, Process process) throws IOException {
            this.name = name;
            this.process = process;
            this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        }

        void send(String line) throws IOException {
            writer.write(line);
            writer.newLine();
            writer.flush();
        }
    }

    public static void main(String[] args) throws Exception {

        // ---- 读取 scripts.json ----
        String jsonText = Files.readString(Paths.get("scripts.json"));

        // 简易 JSON 解析（无需第三方库，适配 ["cmd1","cmd2"]）
        List<String> commands = parseSimpleJsonArray(jsonText);

        System.out.println("读取到脚本命令:");
        for (int i = 0; i < commands.size(); i++) {
            System.out.println("S" + (i + 1) + ": " + commands.get(i));
        }

        // 脚本列表
        List<Script> scripts = new ArrayList<>();

        // ---- 启动每个脚本 ----
        for (int i = 0; i < commands.size(); i++) {
            String name = "S" + (i + 1);
            String command = commands.get(i);

            ProcessBuilder pb = new ProcessBuilder(splitCommand(command));
            pb.redirectErrorStream(true); // 将 stderr 合并到 stdout

            try {
                Process p = pb.start();
                Script script = new Script(name, p);
                scripts.add(script);

                // 输出监控线程
                new Thread(() -> readOutput(script)).start();

                System.out.println("已启动脚本 " + name + ": " + command);

            } catch (IOException e) {
                System.out.println("脚本启动失败 " + name + ": " + e);
            }
        }

        System.out.println("\n输入格式: S1: 指令内容");
        System.out.println("例如: S2: hello world");

        // ---- 输入路由 ----
        Scanner scanner = new Scanner(System.in);
        Pattern pattern = Pattern.compile("(S\\d+):\\s*(.*)");

        while (true) {
            String line = scanner.nextLine();

            Matcher m = pattern.matcher(line);
            if (!m.matches()) {
                System.out.println("格式错误，应为: S1: 命令内容");
                continue;
            }

            String target = m.group(1);
            String cmd = m.group(2);

            Script found = null;
            for (Script s : scripts) {
                if (s.name.equals(target)) {
                    found = s;
                    break;
                }
            }

            if (found != null) {
                try {
                    found.send(cmd);
                } catch (IOException e) {
                    System.out.println("发送失败: " + e);
                }
            } else {
                System.out.println("未知脚本: " + target);
            }
        }
    }

    // ---- 将脚本输出带前缀打印 ----
    public static void readOutput(Script s) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(s.process.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                System.out.println("[" + s.name + "] " + line);
            }
        } catch (Exception e) {
            System.out.println("读取输出失败: " + e);
        }
    }

    // ---- 解析 ["cmd","cmd2"] ----
    public static List<String> parseSimpleJsonArray(String json) {
        List<String> list = new ArrayList<>();
        json = json.trim();

        if (json.startsWith("[") && json.endsWith("]")) {
            json = json.substring(1, json.length() - 1).trim();
        }

        // 按引号分割元素
        String[] arr = json.split("\"");

        for (int i = 1; i < arr.length; i += 2) {
            list.add(arr[i]);
        }

        return list;
    }

    // ---- 分割命令 "python3 a.py" -> ["python3", "a.py"] ----
    public static List<String> splitCommand(String command) {
        return Arrays.asList(command.split(" "));
    }
}
