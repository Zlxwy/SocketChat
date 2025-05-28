import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
    private static final int PORT = 9999; // 服务器监听的端口号
    private static HashMap<String, Socket> clients = new HashMap<>(); // 存储客户端的用户名和对应的 Socket 连接
    private static Set<String> usernames = new HashSet<>(); // 存储已连接的用户名
    private static File fileDirectory = new File("server_files"); // 定义一个 server_files 文件夹用于存储上传文件

    public static void main(String[] args) {
        fileDirectory.mkdir(); // 创建文件夹
        try {
            ServerSocket serverSocket = new ServerSocket(PORT); // 创建一个服务Socket
            System.out.println("服务器启动在端口: " + PORT); // 终端打印端口号

            // 不断循环监听客户端连接，有的话就创建一个新的线程对其进行处理
            while (true) {
                Socket socket = serverSocket.accept(); // 服务器启动并开始监听后，会在这里阻塞执行，直到有一个客户端尝试连接到这个端口。
                new ClientHandler(socket).start(); // 每当有一个新的客户端连接时，创建一个新的 ClientHandler 线程来处理这个连接
                // .start()：启动这个新创建的线程。这意味着 ClientHandler 类中的 run() 方法将会在一个新的线程中执行。
                // 这样可以让服务器在多个线程上同时处理多个客户端连接，而不会阻塞在一个连接上。
            }
        } catch (IOException e) {
            e.printStackTrace(); // 将异常的详细信息打印到标准错误流（通常是控制台）
        }
    }

    static class ClientHandler extends Thread { // ClientHandler 类继承自 Thread 类
        private Socket socket; // 存储客户端的 Socket 连接
        private BufferedReader reader; // 用于读取客户端发送的消息
        private PrintWriter writer; // 用于向客户端发送消息
        private String username; // 存储客户端的用户名

        /* 构造函数，接收一个 Socket 对象作为参数 */
        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                // 通过将原始的字节流socket.getxxxStream()包装为文本流，并添加缓冲读取功能，就能够调用readline()等文本读取方法了
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream())); // 创建一个文本接收对象
                writer = new PrintWriter(socket.getOutputStream(), true); // 创建一共文本发送对象
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run() {
            try {
                String firstMessage = reader.readLine(); // 读取一行内容
                
                // 处理文件下载请求。这是另开一个Socket连接来处理的。因此在handleFileDownload完成后，这个Socket连接就会被关闭，run也会随之结束
                if ("FILE_DOWNLOAD_REQUEST".equals(firstMessage)) { // 如果是文件下载请求
                    String fileName = reader.readLine(); // 再读取一行内容，这行内容是客户端要下载的文件名
                    handleFileDownload(fileName); // 进入 handleFileDownload 方法开始给客户端下载文件
                    socket.close(); // 下载完成直接关闭这个Socket
                    return;
                }
                
                // 正常的聊天连接处理
                username = firstMessage; // 读取第一个消息作为用户名
                if (username == null || usernames.contains(username)) { // 如果用户名为空，或者用户名已存在
                    writer.println("USERNAME_TAKEN"); // 向客户端发送用户名已被占用的消息
                    socket.close(); // 关闭这个Socket连接
                    return; // 结束这个线程
                }
                usernames.add(username); // 将用户名添加到已连接的用户名集合中
                clients.put(username, socket); // 将用户名和对应的 Socket 连接存储在 clients 哈希映射中
                broadcast("SERVER: " + username + " 加入了聊天室"); // 向所有客户端广播新用户加入的消息
                updateUserList();

                String message;
                while ((message = reader.readLine()) != null) {
                    if (message.startsWith("FILE:")) { // 如果消息以 "FILE:" 开头，表示客户端请求上传文件
                        handleFileUpload(); // 处理文件上传
                    } else if (message.startsWith("@")) { // 如果消息以 "@" 开头，表示客户端发送的是私聊消息
                        handlePrivateMessage(message); // 处理私聊消息
                    } else { // 否则，消息是普通的聊天消息
                        broadcast(username + ": " + message); // 向所有客户端广播消息
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally { // 在 finally 块中，无论是否发生异常，都会执行清理操作
                // 无论是因为客户端断开连接、发生异常还是主动return，finally块都会被执行，确保资源被正确清理。
                if (username != null) { // 如果用户名不为空，表示这个客户端之前尝试连接过
                    usernames.remove(username); // 从已连接的用户名集合中移除这个用户名
                    clients.remove(username); // 从 clients 哈希映射中移除这个用户名和对应的 Socket 连接
                    broadcast("SERVER: " + username + " 离开了聊天室"); // 向所有客户端广播用户离开的消息
                    updateUserList(); // 更新在线用户列表
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        /* 处理客户端上传文件 */
        private void handleFileUpload() {
            try {
                String fileName = reader.readLine(); // 读取文件名
                long fileSize = Long.parseLong(reader.readLine()); // 从输入流中读取一行数据，并将其解析为 long 类型，表示文件大小
                
                File file = new File(fileDirectory, fileName); // 创建一个 File 对象，表示要保存的文件
                FileOutputStream fileOutStream = new FileOutputStream(file); // 创建一个 FileOutputStream 对象，用于将数据写入文件
                InputStream inStream = socket.getInputStream(); // 获取 Socket 的输入流，用于读取客户端发送的数据
                
                byte[] buffer = new byte[4096]; // 字节数组，用于存储从输入流中读取的数据
                long remaining = fileSize; // 表示剩余需要读取的字节数，初始值为文件大小
                int read; // 表示每次从输入流中读取的字节数
                
                // 如果剩余字节数大于 0，并且还能从输入流中读取到数据，则继续读取
                // inStream.read(buffer, 0, (int)Math.min(buffer.length, remaining))
                // 表示从输入流中读取最多 buffer.length 或 remaining 字节的数据，并存入 buffer 数组从索引 0 开始的位置
                while (remaining > 0 && (read = inStream.read(buffer, 0, (int)Math.min(buffer.length, remaining))) != -1) {
                    fileOutStream.write(buffer, 0, read);
                    remaining -= read;
                }
                
                fileOutStream.close(); // 关闭文件输出流，目的是释放与该流相关的系统资源，如文件句柄，这有助于防止资源泄漏
                broadcast("SERVER: " + username + " 上传了文件: " + fileName); // 向所有客户端广播文件上传的消息
                updateFileList(); // 更新文件列表
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /* 处理客户端下载文件请求 */
        private void handleFileDownload(String fileName) {
            try {
                File file = new File(fileDirectory, fileName); // 创建一个 File 对象，表示要下载的文件
                if (!file.exists()) { // 检查文件是否存在
                    writer.println("FILE_NOT_FOUND"); // 如果文件不存在，向客户端发送文件未找到的消息
                    return; // 结束方法
                }
                // 如果文件存在，将会执行到此处
                writer.println("FILE_START"); // 发送“FILE_START”，提示客户端文件传输开始
                writer.println(file.length()); // 发送文件大小，客户端需要知道文件大小以便正确接收文件

                FileInputStream fileInStream = new FileInputStream(file); // 打开文件输入流 fileInStream，用于读取文件内容
                OutputStream outStream = new BufferedOutputStream(socket.getOutputStream()); // 打开客户端输出流 outStream，用于将文件内容发送给客户端
                
                byte[] buffer = new byte[4096];
                int read;
                while ((read = fileInStream.read(buffer)) != -1) { // 读取文件内容
                    outStream.write(buffer, 0, read); // 通过输出流发送文件内容
                }
                outStream.flush(); // 刷新输出流，确保所有数据都被发送到客户端
                fileInStream.close(); // 关闭文件输入流，释放与该流相关的系统资源

                Thread.sleep(100); // 等待100毫秒，确保客户端能够正确接收文件
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }

        /* 处理私聊消息，私聊消息的格式通常是 @recipient content，所以第一个空格将分隔接收者和消息内容。 */
        private void handlePrivateMessage(String message) {
            int firstSpace = message.indexOf(" "); // 查找消息中第一个空格的位置。私聊消息的格式通常是 @recipient content，所以第一个空格将分隔接收者和消息内容。
            if (firstSpace != -1) { // 检查是否找到了空格。如果没有找到空格，说明消息格式不正确，方法将直接返回
                String recipient = message.substring(1, firstSpace); // 从消息的第二位（跳过@符号）到第一个空格之间的字符串作为接收者用户名。
                String content = message.substring(firstSpace + 1); // 从第一个空格之后到消息末尾的字符串作为消息内容
                Socket recipientSocket = clients.get(recipient); // 使用接收者用户名从clients哈希映射中获取对应的Socket对象。clients`是一个存储用户名和对应 `Socket` 的哈希映射。
                
                if (recipientSocket != null) { // 如果客户端存在
                    try {
                        PrintWriter recipientWriter = new PrintWriter(recipientSocket.getOutputStream(), true);
                        // 获取接收者 `Socket` 的输出流，并创建一个 `PrintWriter` 对象，用于向接收者发送消息。
                        recipientWriter.println("私聊自 " + username + ": " + content); // 向接收者发送私聊消息。消息格式为“私聊自 发送者: 消息内容”。
                        writer.println("私聊给 " + recipient + ": " + content); // 向发送者确认私聊消息已发送。消息格式为“私聊给 接收者: 消息内容”。
                    } catch (IOException e) { // 捕获可能发生的 `IOException`
                        e.printStackTrace(); // 打印异常的堆栈跟踪
                    }
                }
            }
        }

        /* 向所有已连接的客户端广播消息 */
        private void broadcast(String message) {
            for (Socket clientSocket : clients.values()) { // 遍历所有已连接的客户端的value(Socket)
                try {
                    PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
                    // 获取每个客户端的输出流，并创建一个 PrintWriter 对象，用于向客户端发送消息
                    writer.println(message); // 使用这个 PrintWriter 对象向客户端发送消息
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        /* 更新在线用户列表，实为向所有客户端广播一条用户列表消息，用于提示客户端界面更新当前在线用户列表 */
        private void updateUserList() {
            StringBuilder userlist = new StringBuilder("USERS:"); // 这一条消息的格式为“USERS: 用户1,用户2,...”
            // 这里使用 StringBuilder 来构建字符串，StringBuilder 是一个可变的字符序列，适合用于频繁修改字符串的场景。
            for (String user: usernames) { // 遍历所有已连接的用户名
                userlist.append(user).append(","); // 将每个用户名添加到 userlist 中，并用逗号分隔
            }
            broadcast(userlist.toString()); // 向所有客户端广播用户列表
        }

        /* 更新文件列表，实为向所有客户端广播一条文件列表消息，用于提示客户端界面更新当前在线用户列表 */
        private void updateFileList() {
            StringBuilder filelist = new StringBuilder("FILES:"); // 这一条消息的格式为“FILES: 文件1,文件2,...”
            for (File file: fileDirectory.listFiles()) { // 遍历 server_files 文件夹中的所有文件
                filelist.append(file.getName()).append(","); // 将每个文件的名称添加到 filelist 中，并用逗号分隔
            }
            broadcast(filelist.toString()); // 向所有客户端广播文件列表
        }
    }
} 