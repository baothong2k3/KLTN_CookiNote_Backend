# ----- Giai đoạn 1: Build ứng dụng -----
# Sử dụng image chứa JDK 17 (dựa trên pom.xml của bạn)
FROM eclipse-temurin:17-jdk-focal AS builder

# Đặt thư mục làm việc
WORKDIR /workspace

# Copy các file build của Maven trước để tận dụng cache
COPY .mvn .mvn
COPY mvnw pom.xml ./

# Copy toàn bộ mã nguồn
COPY src src

# Chạy lệnh build của Maven (giống như bạn làm ở local)
# Chúng ta dùng 'package' thay vì 'install' và bỏ qua test
RUN ./mvnw clean package -DskipTests

# ----- Giai đoạn 2: Build image chạy -----
# Sử dụng image JRE (Java Runtime) 17 cho nhẹ
FROM eclipse-temurin:17-jre-focal

# Đặt thư mục làm việc
WORKDIR /app

# Tạo một user không phải root để chạy ứng dụng (bảo mật hơn)
RUN groupadd -r cnote && useradd -r -g cnote cnote
USER cnote

# Copy file .jar đã được build từ giai đoạn 'builder'
# Tên file .jar này được lấy từ pom.xml của bạn
COPY --from=builder /workspace/target/KLTN_CookiNote_Backend-0.0.1-SNAPSHOT.jar app.jar

# Render yêu cầu ứng dụng chạy trên cổng 10000
EXPOSE 10000

# Lệnh để khởi động ứng dụng
ENTRYPOINT ["java", "-jar", "app.jar"]