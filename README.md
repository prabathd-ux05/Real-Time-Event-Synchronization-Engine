# 🚀 Real-Time Event Sync Engine "TweetX"

A full-stack real-time messaging application built using Spring Boot and WebSocket, inspired by Instagram DMs. This project demonstrates real-time communication, media handling, and persistent chat storage with a clean modern UI.

---

## 🎥 Demo

👉 Watch the demo video here:  
[▶️ Watch Demo](https://drive.google.com/file/d/1ULnT7dYohjQ_oIHFZywwxaxyP5liCw5K/preview)

---

## ✨ Features

* ⚡ Real-time messaging using WebSocket
* 🖼️ Media sharing (images & videos) via REST APIs
* 💾 Persistent chat storage (H2 Database)
* 🟢 Live online/offline user status
* 🔐 User authentication with session persistence
* 🎨 Clean dark UI with smooth chat experience
* 📂 Auto-loading chat history on conversation open

---

## 🧰 Tech Stack

**Backend:**

* Java
* Spring Boot
* WebSocket

**Database:**

* H2 Database
* JPA / Hibernate

**Frontend:**

* HTML
* CSS
* JavaScript

**Other:**

* REST APIs (Authentication, File Upload, Message History)

---

## 🔄 System Flow

```text
User Login → WebSocket Connect → Send Message → Media Upload → Load Chat History → Presence Update
```

---

## 📦 Project Setup


### 1. Run the Backend

* Open in IDE (Eclipse / IntelliJ)
* Run the main Spring Boot application

### 2. Access the Application

```
http://localhost:8080
```

### 3. H2 Database Console

```
http://localhost:8080/h2-console
```

Use:

```
JDBC URL: jdbc:h2:file:./data/chatdb
Username: sa
Password: (leave empty)
```

---

## 📚 What I Learned

* Real-time communication using WebSocket in Spring Boot
* Database persistence with JPA/Hibernate
* Designing REST APIs for scalable applications
* Separating real-time events from heavy operations (media upload)
* Building a complete full-stack system from scratch

---

## 📬 Contact

If you’d like to connect or collaborate, feel free to reach out!

---

## ⭐ Show Your Support

If you like this project, give it a ⭐ on GitHub!
