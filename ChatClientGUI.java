import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;

// ChatClientGUI 类继承自 JFrame，用于创建聊天客户端的图形用户界面。
public class ChatClientGUI extends JFrame {
    private JTextArea chatArea; // 聊天区域
    private JTextField messageField;  // 输入消息的文本框
    private JList<String> userList; // 在线用户列表
    private JList<String> fileList; // 文件列表
    private DefaultListModel<String> userListModel; // 在线用户列表模型
    private DefaultListModel<String> fileListModel; // 文件列表模型
    private Socket socket; // 客户端套接字
    private PrintWriter writer; // 用于向服务器发送消息的 PrintWriter
    private BufferedReader reader; // 用于从服务器读取消息的 BufferedReader
    private String username; // 用户名
    private File downloadDirectory; // 下载目录

    public ChatClientGUI() {
        // 可以不调用super()，继承JFrame类后，默认会调用父类的无参构造函数
        downloadDirectory = new File("downloads"); // 创建下载目录
        downloadDirectory.mkdir();
        
        // 获取用户名
        username = JOptionPane.showInputDialog("请输入用户名:"); // 弹出对话框，包含一个用户名输入框
        if (username == null || username.trim().isEmpty()) { // 如果输入的用户名为空、输入用户名只包含空格（.trim方法用于去掉字符串两端的空格）
            System.exit(0); // 退出程序
        }
        this.setTitle(username + " 的聊天室"); // 设置窗口标题为用户名 + " 的聊天室"

        // 初始化GUI组件
        this.setLayout(new BorderLayout()); // 设置窗口布局为 BorderLayout（边界布局）
        
        // 聊天区域
        chatArea = new JTextArea(); // 创建一个文本区域，用于显示聊天记录
        chatArea.setEditable(false); // 设置文本区域不可编辑
        chatArea.setLineWrap(true); // 设置文本区域自动换行
        this.add(new JScrollPane(chatArea), BorderLayout.CENTER); // 将聊天区域放入一个滚动面板中，添加到窗口的中心位
        
        // 用户列表
        userListModel = new DefaultListModel<>(); // 创建一个DefaultListModel，用于存储在线用户
        userList = new JList<>(userListModel); // 创建一个JList，用于显示在线用户
        // DefaultListModel用来存储字符串数据，并创建了一个JList来显示这些数据。将数据和视图分离，使得数据的管理和界面的显示可以独立进行。
        userList.addMouseListener(new MouseAdapter() { // 给用户列表添加鼠标监听器
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) { // 如果鼠标对列表中的某个元素双击
                    String selectedUser = userList.getSelectedValue(); // 获取选中的元素
                    if (selectedUser != null && !selectedUser.equals(username)) { // 如果选中的元素不为空，并且不是当前用户
                        messageField.setText("@" + selectedUser + " "); // 在输入框中填入私聊消息的格式：@用户名
                        messageField.requestFocus(); // 设置输入框为焦点
                        messageField.setCaretPosition(messageField.getText().length()); // 将光标移动到文本的末尾
                    }
                }
            }
        });
        
        // 文件列表
        fileListModel = new DefaultListModel<>(); // 创建一个DefaultListModel，用于存储文件列表
        fileList = new JList<>(fileListModel); // 创建一个JList，用于显示文件列表
        
        // 给用户列表和文件列表添加标签
        JLabel userListLabel = new JLabel("在线用户（双击用户名私聊）"); // 创建一个标签，用于显示在线用户列表的标题
        JLabel fileListLabel = new JLabel("文件列表（双击文件下载）"); // 创建一个标签，用于显示文件列表的标题
        
        // 用户列表面板，包含用户列表（存放在一个滚动面板中）和标签
        JPanel userPanel = new JPanel(new BorderLayout()); // 创建一个面板，用于存放在线用户列表
        userPanel.add(userListLabel, BorderLayout.NORTH); // 将标签添加到面板的北边（顶部）
        userPanel.add(new JScrollPane(userList), BorderLayout.CENTER); // 将用户列表添加到面板的中心位置
        
        // 文件列表面板，包含文件列表（存放在一个滚动面板中）和标签
        JPanel filePanel = new JPanel(new BorderLayout()); // 创建一个面板，用于存放文件列表
        filePanel.add(fileListLabel, BorderLayout.NORTH); // 将标签添加到面板的北边（顶部）
        filePanel.add(new JScrollPane(fileList), BorderLayout.CENTER); // 将文件列表添加到面板的中心位置
        
        // 右侧面板，包含在线用户列表面板和文件列表面板
        JPanel rightPanel = new JPanel(new GridLayout(2, 1)); // 创建一个两行一列的面板
        rightPanel.setLayout(new GridLayout(2, 1, 5, 5));
        rightPanel.add(userPanel); // 将在线用户列表面板添加到右侧面板
        rightPanel.add(filePanel); // 将文件列表面板添加到右侧面板

        // 发送按钮
        JButton sendButton = new JButton("发送"); // 创建一个按钮，用于发送消息

        // 上传文件按钮
        JButton fileButton = new JButton("上传文件"); // 创建一个按钮，用于上传文件

        // 按钮面板，包含发送按钮和上传文件按钮
        JPanel buttonPanel = new JPanel(new FlowLayout()); // 创建一个按钮面板，用于存放按钮
        buttonPanel.add(sendButton); // 将发送按钮添加到按钮面板
        buttonPanel.add(fileButton); // 将上传文件按钮添加到按钮面板
        
        // 底部面板，包含输入框和按钮面板
        JPanel bottomPanel = new JPanel(new BorderLayout()); // 创建一个底部面板，用于存放输入框和按钮面板
        messageField = new JTextField(); // 创建一个文本框，用于输入消息
        bottomPanel.add(messageField, BorderLayout.CENTER); // 将输入框添加到底部面板的中心位置
        bottomPanel.add(buttonPanel, BorderLayout.EAST); // 将按钮面板添加到底部面板的东边（右侧）
        
        this.add(rightPanel, BorderLayout.EAST); // 将右侧面板添加到窗口的东边（右侧）
        this.add(bottomPanel, BorderLayout.SOUTH); // 将底部面板添加到窗口的南边（底部）

        // 添加事件监听器
        messageField.addActionListener(e -> sendMessage()); // 当按下回车键时，发送消息
        sendButton.addActionListener(e -> sendMessage()); // 当点击发送按钮时，发送消息
        fileButton.addActionListener(e -> uploadFile()); // 当点击上传文件按钮时，上传文件
        // ActionListener 接口类有一个抽象方法 actionPerformed(ActionEvent e)
        // e->sendMessage() 是一个 lambda 表达式，重写了 actionPerformed 方法，
        // 当按钮被点击时，将会调用 sendMessage 方法。
        
        fileList.addMouseListener(new MouseAdapter() { // 给文件列表添加鼠标监听器
            @Override
            public void mouseClicked(MouseEvent e) { // 当鼠标点击文件列表时
                if (e.getClickCount() == 2) { // 如果鼠标双击文件列表中的某个元素
                    String selectedFile = fileList.getSelectedValue(); // 获取选中的元素
                    if (selectedFile != null) { // 如果选中的元素不为空
                        downloadFile(selectedFile); // 下载选中的文件
                    }
                }
            }
        });

        // 设置窗口属性
        this.setSize(800, 600); // 设置窗口大小为800x600
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // 设置关闭窗口时退出程序
        this.setLocationRelativeTo(null); // 设置窗口在屏幕中央显示

        // 连接到服务器
        connectToServer(); // 连接到服务器，并启动接收消息的线程
    }

    /* 在面板初始化完成后调用的方法 */
    private void connectToServer() {
        try {
            socket = new Socket("localhost", 9999); // 创建一个Socket连接到服务器，IP地址为localhost，端口号为9999
            writer = new PrintWriter(socket.getOutputStream(), true); // 创建一个PrintWriter，用于向服务器发送消息
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream())); // 创建一个BufferedReader，用于从服务器接收消息

            // 发送用户名
            writer.println(username); // 连接服务器第一件事，发送用户名
            
            // 启动接收消息的线程
            new Thread(() -> { // 创建一个线程，用于接收服务器发送的消息
                try {
                    String message; // 用于存储从服务器接收到的消息
                    while ((message = reader.readLine()) != null) { // 读到一行消息，并且不为空
                        if (message.equals("USERNAME_TAKEN")) { // 如果消息是“USERNAME_TAKEN”，则表示用户名已被使用
                            JOptionPane.showMessageDialog(this, "用户名已被使用"); // 提示用户名已被使用
                            System.exit(0); // 退出程序
                        } else if (message.startsWith("USERS:")) { // 如果消息以“USERS:”开头，则表示消息是用户列表
                            updateUserList(message.substring(6)); // 更新用户列表
                        } else if (message.startsWith("FILES:")) { // 如果消息以“FILES:”开头，则表示消息是文件列表
                            updateFileList(message.substring(6)); // 更新文件列表
                        } else { // 其他：普通聊天消息
                            chatArea.append(message + "\n"); // 将消息添加到聊天窗口中
                            chatArea.setCaretPosition(chatArea.getDocument().getLength()); // 设置光标位置到文本的末尾
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "无法连接到服务器");
            System.exit(1);
        }
    }

    /* 发送消息 */
    private void sendMessage() {
        String message = messageField.getText().trim(); // 获取输入框中的消息，并去掉前后空格
        if (!message.isEmpty()) { // 如果消息不为空
            if (message.startsWith("@")) { // 如果是私聊消息
                int spaceIndex = message.indexOf(" "); // 查找第一个空格的位置
                if (spaceIndex > 1) { // 如果空格位置大于1，表示有接收者（@u ）
                    String recipient = message.substring(1, spaceIndex); // 获取接收者的用户名（索引左闭右开）
                    boolean isRecipientOnline = false; // 检查接收者是否在线
                    for (int i=0; i<userListModel.getSize(); i++) { // 遍历在线用户列表
                        if (userListModel.getElementAt(i).equals(recipient)) { // 如果找到接收者
                        // getElementAt(i) 是 DefaultListModel 类中的一个方法，用于获取列表中指定索引 i 处的元素
                            isRecipientOnline = true;
                            break;
                        }
                    }
                    if (!isRecipientOnline) { // 如果接收者不在线
                        JOptionPane.showMessageDialog(this, "用户 " + recipient + " 不在线");
                        return;
                        // 弹窗显示“用户xxx不在线”，然后返回，不发送消息
                    }
                }
            }
            // 如果是群聊消息，或者私聊消息的接收者在线，将会来到此处
            writer.println(message); // 正常发送消息
            messageField.setText(""); // 清空输入框
        }
    }

    /* 上传文件 */
    private void uploadFile() {
        JFileChooser fileChooser = new JFileChooser(); // 创建一个文件选择器（文件选择窗口），用于选择要上传的文件
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) { // 如果选择了文件
            File file = fileChooser.getSelectedFile(); // 获取选择的文件
            try {
                writer.println("FILE:"); // 发送文件上传请求
                writer.println(file.getName()); // 发送文件名
                writer.println(file.length()); // 发送文件大小

                FileInputStream fileInStream = new FileInputStream(file); // 创建一个文件输入流，用于读取文件内容
                OutputStream outStream = socket.getOutputStream(); // 获取Socket的输出流，用于向服务器发送文件内容
                
                byte[] buffer = new byte[4096];
                int read;
                while ((read = fileInStream.read(buffer)) != -1) { // 读取文件内容
                    outStream.write(buffer, 0, read); // 通过输出流发送文件内容
                }
                outStream.flush(); // 刷新输出流，确保所有数据都被发送
                fileInStream.close(); // 关闭文件输入流
            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "文件上传失败");
            }
        }
    }

    private void downloadFile(String fileName) {
        try {
            // 创建新的 socket 连接专门用于文件传输
            Socket fileSocket = new Socket("localhost", 9999); // 连接到服务器
            PrintWriter fileWriter = new PrintWriter(fileSocket.getOutputStream(), true); // 创建一个 PrintWriter，用于向服务器发送文件下载请求
            BufferedReader fileReader = new BufferedReader(new InputStreamReader(fileSocket.getInputStream())); // 创建一个 BufferedReader，用于从服务器读取文件下载响应
            
            // 发送下载请求
            fileWriter.println("FILE_DOWNLOAD_REQUEST"); // 发送文件下载请求消息
            fileWriter.println(fileName); // 发送文件名
            
            String response = fileReader.readLine();
            
            if (response.equals("FILE_NOT_FOUND")) {
                JOptionPane.showMessageDialog(this, "文件不存在");
                fileSocket.close();
                return;
            }
            
            if (response.equals("FILE_START")) {
                long fileSize = Long.parseLong(fileReader.readLine());
                File file = new File(downloadDirectory, fileName);
                FileOutputStream fileOutStream = new FileOutputStream(file);
                InputStream inStream = fileSocket.getInputStream();
                
                byte[] buffer = new byte[4096];
                long remaining = fileSize;
                int read;
                
                while (remaining > 0 && (read = inStream.read(buffer, 0, (int)Math.min(buffer.length, remaining))) != -1) {
                    fileOutStream.write(buffer, 0, read);
                    remaining -= read;
                }
                
                fileOutStream.close();
                fileSocket.close();
                
                JOptionPane.showMessageDialog(this, "文件下载完成: " + file.getAbsolutePath());
            }
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "文件下载失败");
        }
    }

    /* 更新用户列表 */
    private void updateUserList(String users) {
        userListModel.clear(); // 清空用户列表
        for (String user : users.split(",")) { // 遍历用户列表(以逗号分隔)
            // split方法用于将字符串分割成数组，返回一个字符串数组
            if (!user.isEmpty()) { // 如果用户不为空
                userListModel.addElement(user); // 将用户添加到用户列表模型中
            }
        }
    }

    /* 更新文件列表 */
    private void updateFileList(String files) {
        fileListModel.clear(); // 清空文件列表
        for (String file: files.split(",")) { // 遍历文件列表(以逗号分隔)
            // split方法用于将字符串分割成数组，返回一个字符串数组
            if (!file.isEmpty()) { // 如果文件不为空
                fileListModel.addElement(file); // 将文件添加到文件列表模型中
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater( () -> {new ChatClientGUI().setVisible(true);} );
        // SwingUtilities.invokeLater将任务提交到EDT（事件调度线程）后，EDT会负责创建和显示GUI。
        // 因为EDT是一个非守护线程，因此主线程会等待EDT完成其任务。
        // EDT继续运行，创建好窗口后，来到方法connectToServer()
        // 在connectToServer()方法中，创建一个Socket连接到服务器，并启动一个新的线程，在其中循环接收服务器的消息。
        // 由于这个循环线程也是一个非守护线程，因此主线程也会等待这个线程运行，直到退出条件满足。
    }
} 