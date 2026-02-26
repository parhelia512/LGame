#include "SocketSupport.h"
#define BUFFER_SIZE 1024

static inline void close_socket(int sock) {
#ifdef _WIN32
    closesocket(sock);
#else
    close(sock);
#endif
}

int get_remote_file_size_socket(const char* url, int64_t* size) {
    if (!url || !size) return -1;
    *size = -1;
    const char* prefix_http = "http://";
    const char* prefix_https = "https://";
    int is_https = 0;
    const char* host_start = NULL;
    if (strncmp(url, prefix_http, strlen(prefix_http)) == 0) {
        host_start = url + strlen(prefix_http);
    }
    else if (strncmp(url, prefix_https, strlen(prefix_https)) == 0) {
        host_start = url + strlen(prefix_https);
        is_https = 1;
    }
    else {
        return -2; 
    }
    const char* path_start = strchr(host_start, '/');
    char host[256] = { 0 };
    char path[512] = { 0 };
    if (path_start) {
        chars_strncpy(host, host_start, path_start - host_start);
        chars_strncpy(path, path_start, sizeof(path) - 1);
    }
    else {
        chars_strncpy(host, host_start, sizeof(host) - 1);
        chars_strcpy(path, sizeof(path) - 1, "/");
    }
    const char* port_str = chars_strchr(host, ':',1);
    char port[6] = "80";
    if (port_str) {
        chars_strncpy(port, port_str + 1, sizeof(port) - 1);
        host[port_str - host] = '\0';
    }
    else if (is_https) {
        chars_strcpy(port, sizeof(port), "443");
    }

#ifdef _WIN32
    WSADATA wsa;
    if (WSAStartup(MAKEWORD(2, 2), &wsa) != 0) {
        return SOCKET_ERR_CONNECT;
    }
#endif
    struct addrinfo hints, * res;
    memset(&hints, 0, sizeof(hints));
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;
    if (getaddrinfo(host, port, &hints, &res) != 0) {
#ifdef _WIN32
        WSACleanup();
#endif
        return SOCKET_ERR_SEND;
    }
    int sock = (int)socket(res->ai_family, res->ai_socktype, res->ai_protocol);
    if (sock < 0) {
        freeaddrinfo(res);
#ifdef _WIN32
        WSACleanup();
#endif
        return SOCKET_ERR_RECV;
    }
    if (connect(sock, res->ai_addr, (int)res->ai_addrlen) != 0) {
        close_socket(sock);
        freeaddrinfo(res);
#ifdef _WIN32
        WSACleanup();
#endif
        return SOCKET_ERR_TIMEOUT;
    }
    freeaddrinfo(res);
    char request[1024];
    snprintf(request, sizeof(request),
        "HEAD %s HTTP/1.1\r\n"
        "Host: %s\r\n"
        "Connection: close\r\n\r\n",
        path, host);
    if (send(sock, request, (int)strlen(request), 0) < 0) {
        close_socket(sock);
#ifdef _WIN32
        WSACleanup();
#endif
        return SOCKET_ERR_NOMEM;
    }
    char buffer[BUFFER_SIZE];
    int bytes;
    while ((bytes = recv(sock, buffer, sizeof(buffer) - 1, 0)) > 0) {
        buffer[bytes] = '\0';
        char* cl = chars_strcasestr(buffer, "Content-Length:",256);
        if (cl) {
            cl += 15; 
            while (*cl && isspace((unsigned char)*cl)) cl++;
            *size = atoll(cl);
            break;
        }
    }
    close_socket(sock);
#ifdef _WIN32
    WSACleanup();
#endif
    return (*size >= 0) ? 0 : -8;
}

#ifdef _WIN32
uint8_t* download_url_windows(const wchar_t* host, INTERNET_PORT port, const wchar_t* path,
    size_t* out_bit_count, long* out_http_code, int* status_code) {
    HINTERNET hSession = WinHttpOpen(L"WinHTTP Downloader/1.0",
        WINHTTP_ACCESS_TYPE_DEFAULT_PROXY,
        WINHTTP_NO_PROXY_NAME,
        WINHTTP_NO_PROXY_BYPASS, 0);
    if (!hSession) { *status_code = -1; return NULL; }

    HINTERNET hConnect = WinHttpConnect(hSession, host, port, 0);
    if (!hConnect) { WinHttpCloseHandle(hSession); *status_code = -2; return NULL; }

    HINTERNET hRequest = WinHttpOpenRequest(hConnect, L"GET", path,
        NULL, WINHTTP_NO_REFERER,
        WINHTTP_DEFAULT_ACCEPT_TYPES,
        (port == INTERNET_DEFAULT_HTTPS_PORT) ? WINHTTP_FLAG_SECURE : 0);
    if (!hRequest) {
        WinHttpCloseHandle(hConnect);
        WinHttpCloseHandle(hSession);
        *status_code = SOCKET_ERR_CONNECT;
        return NULL;
    }

    if (!WinHttpSendRequest(hRequest, WINHTTP_NO_ADDITIONAL_HEADERS, 0,
        WINHTTP_NO_REQUEST_DATA, 0, 0, 0) ||
        !WinHttpReceiveResponse(hRequest, NULL)) {
        WinHttpCloseHandle(hRequest);
        WinHttpCloseHandle(hConnect);
        WinHttpCloseHandle(hSession);
        *status_code = SOCKET_ERR_SEND;
        return NULL;
    }

    DWORD status = 0, size = sizeof(status);
    WinHttpQueryHeaders(hRequest,
        WINHTTP_QUERY_STATUS_CODE | WINHTTP_QUERY_FLAG_NUMBER,
        WINHTTP_HEADER_NAME_BY_INDEX,
        &status, &size, WINHTTP_NO_HEADER_INDEX);
    *out_http_code = status;

    uint8_t* buffer = NULL;
    size_t total_size = 0;
    DWORD bytes_read = 0;
    do {
        uint8_t temp[4096];
        if (!WinHttpReadData(hRequest, temp, sizeof(temp), &bytes_read)) break;
        if (bytes_read > 0) {
            uint8_t* new_buf = (uint8_t*)realloc(buffer, total_size + bytes_read);
            if (!new_buf) { free(buffer); buffer = NULL; break; }
            buffer = new_buf;
            memcpy(buffer + total_size, temp, bytes_read);
            total_size += bytes_read;
        }
    } while (bytes_read > 0);

    WinHttpCloseHandle(hRequest);
    WinHttpCloseHandle(hConnect);
    WinHttpCloseHandle(hSession);

    if (!buffer) { *status_code = -5; return NULL; }

    *out_bit_count = total_size * 8;
    *status_code = 0;
    return buffer;
}
#else
struct MemoryBlock { uint8_t* data; size_t size; };
static size_t WriteMemoryCallback(void* contents, size_t size, size_t nmemb, void* userp) {
    size_t totalSize = size * nmemb;
    struct MemoryBlock* mem = (struct MemoryBlock*)userp;
    uint8_t* ptr = realloc(mem->data, mem->size + totalSize);
    if (!ptr) return 0;
    mem->data = ptr;
    memcpy(&(mem->data[mem->size]), contents, totalSize);
    mem->size += totalSize;
    return totalSize;
}

uint8_t* download_url_unix(const char* url, size_t* out_bit_count, long* out_http_code, int* status_code) {
    CURL* curl = curl_easy_init();
    if (!curl) { *status_code = -1; return NULL; }
    struct MemoryBlock chunk = { 0 };
    curl_easy_setopt(curl, CURLOPT_URL, url);
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, WriteMemoryCallback);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, (void*)&chunk);
    curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 1L);
    CURLcode res = curl_easy_perform(curl);
    if (res != CURLE_OK) { curl_easy_cleanup(curl); free(chunk.data); *status_code = -2; return NULL; }
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, out_http_code);
    curl_easy_cleanup(curl);
    *out_bit_count = chunk.size * 8;
    *status_code = 0;
    return chunk.data;
}
#endif

uint8_t* download_with_retry(
#ifdef _WIN32
    const wchar_t* host, INTERNET_PORT port, const wchar_t* path,
#else
    const char* url,
#endif
    size_t* out_bit_count, long* out_http_code, int* status_code,
    int max_retries, int retry_delay_ms, const long* retry_http_codes, size_t retry_http_codes_count) {
    for (int attempt = 0; attempt <= max_retries; attempt++) {
#ifdef _WIN32
        uint8_t* data = download_url_windows(host, port, path, out_bit_count, out_http_code, status_code);
#else
        uint8_t* data = download_url_unix(url, out_bit_count, out_http_code, status_code);
#endif
        if (data && *status_code == 0) {
            int should_retry = 0;
            if (retry_http_codes && retry_http_codes_count > 0) {
                for (size_t i = 0; i < retry_http_codes_count; i++) {
                    if (*out_http_code == retry_http_codes[i]) {
                        should_retry = 1;
                        break;
                    }
                }
            }
            if (!should_retry) {
                return data;
            }
            free(data);
        }
        if (attempt < max_retries) {
            run_sleep_ms(retry_delay_ms);
        }
    }
    *status_code = SOCKET_ERR_UNKNOWN;
    return NULL;
}

void DownloadURL(const char* url,uint8_t* outBytes, int32_t len)
{
    uint8_t* packed_bits = NULL;
    size_t bit_count = 0;
    long http_code = 0;
    int status = 0;
    long retry_codes[] = { 500, 502, 503 };
#ifdef _WIN32
    packed_bits = download_with_retry(
        (const wchar_t*)url, INTERNET_DEFAULT_HTTPS_PORT, L"/",
#else
    packed_bits = download_with_retry(
        url,
#endif
        & bit_count, &http_code, &status,
        3,
        2000,
        retry_codes, sizeof(retry_codes) / sizeof(retry_codes[0])
    );
    if (packed_bits && status == 0) {
        copy_uint8_array(outBytes, len, packed_bits,len);
        free(packed_bits);
        return;
    }
    else {
        fprintf(stderr, "Download failed after retries. Status code: %d\n", status);
    }
    return;
}

void ImportSocketInclude()
{
}

int64_t GetURLFileSize(const char* url)
{
    int64_t size = 0;
    int ret = get_remote_file_size_socket(url, &size);
    if (ret == 0) {
        return size;
    }
    else {
        return -1;
    }
    return -1;
}

int Load_Socket_Init()
{
    #if defined(_WIN32) || defined(_WIN64) || defined(_XBOX)
        WSADATA wsa;
        if (WSAStartup(MAKEWORD(2, 2), &wsa) != 0) return SOCKET_ERR_PARAM;
        return 0;
    #elif defined(__SWITCH__)
        return socketInitializeDefault();
    #elif defined(__3DS__) || defined(__WIIU__)
        return socInit(NULL, 0);
    #elif defined(__ORBIS__) || defined(__PROSPERO__) || defined(__VITA__)
        return sceNetInit();
    #elif defined(__PSP__)
        if (sceNetInit() != 0) return SOCKET_ERR_PARAM;
        if (sceNetInetInit() != 0) return SOCKET_ERR_INIT;
        return 0;
    #else
        return 0; 
    #endif
}

void Load_Socket_Free() {
#if defined(__PSP__)
    sceNetInetTerm();
    sceNetTerm();
#elif defined(_WIN32) || defined(_WIN64) || defined(_XBOX)
    WSACleanup();
#elif defined(__SWITCH__)
    socketExit();
#elif defined(__3DS__) || defined(__WIIU__)
    socExit();
#elif defined(__ORBIS__) || defined(__PROSPERO__) || defined(__VITA__)
    sceNetTerm();
#endif
}

void Load_Socket_Close(socket_t sock) {
#if defined(__PSP__)
    sceNetInetClose(sock);
#elif defined(_WIN32) || defined(_WIN64) || defined(_XBOX)
    closesocket(sock);
#else
    close(sock);
#endif
}

socket_t Load_Connect_Server(int port) {
#if defined(_WIN32) || defined(_WIN64) || defined(_XBOX)
    WSADATA wsa;
    if (WSAStartup(MAKEWORD(2, 2), &wsa) != 0) {
        return SOCKET_ERR_INIT;
    }
    socket_t server_fd = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
#elif defined(__PSP__)
    if (sceNetInit() != 0 || sceNetInetInit() != 0) {
        return SOCKET_ERR_INIT;
    }
    socket_t server_fd = sceNetInetSocket(AF_INET, SOCK_STREAM, 0);
#else
    socket_t server_fd = socket(AF_INET, SOCK_STREAM, 0);
#endif
    if (server_fd < 0) return SOCKET_ERR_INIT;
    int opt = 1;
#if defined(__PSP__)
    sceNetInetSetsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR, (const char*)&opt, sizeof(opt));
#else
    setsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR, (const char*)&opt, sizeof(opt));
#endif
    struct sockaddr_in addr = { 0 };
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = INADDR_ANY;
    addr.sin_port = htons(port);
#if defined(__PSP__)
    if (sceNetInetBind(server_fd, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
        SS_ClientClose(server_fd);
        return SOCKET_ERR_CONNECT;
    }
    if (sceNetInetListen(server_fd, 5) < 0) {
        SS_ClientClose(server_fd);
        return SOCKET_ERR_CONNECT;
    }
#else
    if (bind(server_fd, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
        Load_Socket_Close(server_fd);
        return SOCKET_ERR_CONNECT;
    }
    if (listen(server_fd, 5) < 0) {
        Load_Socket_Close(server_fd);
        return SOCKET_ERR_CONNECT;
    }
#endif
    return server_fd;
}

socket_t Load_Connect_Client(const char* host, int port) {
#if defined(__PSP__)
    socket_t sock = sceNetInetSocket(AF_INET, SOCK_STREAM, 0);
#else
    socket_t sock = socket(AF_INET, SOCK_STREAM, 0);
#endif
    if (sock < 0) return SOCKET_ERR_PARAM;
    struct sockaddr_in addr = { 0 };
    addr.sin_family = AF_INET;
    addr.sin_port = htons(port);
#if defined(__PSP__)
    addr.sin_addr.s_addr = sceNetInetAddr(host);
    if (addr.sin_addr.s_addr == -1) {
        sceNetInetClose(sock);
        return SOCKET_ERR_INIT;
    }
    if (sceNetInetConnect(sock, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
        sceNetInetClose(sock);
        return SOCKET_ERR_CONNECT;
    }
#else
    if (inet_pton(AF_INET, host, &addr.sin_addr) <= 0) {
#if defined(_WIN32) || defined(_WIN64) || defined(_XBOX)
        closesocket(sock);
#else
        close(sock);
#endif
        return SOCKET_ERR_INIT;
    }
    if (connect(sock, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
#if defined(_WIN32) || defined(_WIN64) || defined(_XBOX)
        closesocket(sock);
#else
        close(sock);
#endif
        return SOCKET_ERR_CONNECT;
    }
#endif
    return sock;
}

socket_t Load_Create_LinkServerToClient(socket_t server_fd) {
    struct sockaddr_in client_addr;
#if defined(_WIN32) || defined(_WIN64) || defined(_XBOX)
    int client_len = sizeof(client_addr);
    socket_t client_fd = accept(server_fd, (struct sockaddr*)&client_addr, &client_len);
#elif defined(__PSP__)
    int client_len = sizeof(client_addr);
    socket_t client_fd = sceNetInetAccept(server_fd, (struct sockaddr*)&client_addr, &client_len);
#else
    socklen_t client_len = sizeof(client_addr);
    socket_t client_fd = accept(server_fd, (struct sockaddr*)&client_addr, &client_len);
#endif
    return client_fd;
}

int Load_Socket_Send(socket_t sock, const uint8_t* data, int len) {
#if defined(__PSP__)
    return sceNetInetSend(sock, (const char*)data, len, 0);
#elif defined(_WIN32) || defined(_WIN64) || defined(_XBOX)
    return send(sock, (const char*)data, len, 0);
#else
    return send(sock, (const char*)data, len, 0);
#endif
}

int Load_Socket_Recv(socket_t sock, uint8_t* buf, int bufsize) {
#if defined(__PSP__)
    return sceNetInetRecv(sock, buf, bufsize, 0);
#elif defined(_WIN32) || defined(_WIN64) || defined(_XBOX)
    return recv(sock, (char*)buf, bufsize, 0);
#else
    return recv(sock, buf, bufsize, 0);
#endif
}

void Load_Socket_FirstIP(char* out_ip, int out_size, int prefer_ipv6)
{
    if (!out_ip || out_size == 0) {
        return;
    }
    out_ip[0] = '\0';
#if defined(_WIN32) || defined(_WIN64) || defined(_XBOX)
    WSADATA wsaData;
    if (WSAStartup(MAKEWORD(2, 2), &wsaData) != 0) {
        return;
    }
    DWORD bufLen = 15000;
    IP_ADAPTER_ADDRESSES* addresses = (IP_ADAPTER_ADDRESSES*)malloc(bufLen);
    if (!addresses) {
        WSACleanup();
        return;
    }
    if (GetAdaptersAddresses(AF_UNSPEC, GAA_FLAG_SKIP_ANYCAST | GAA_FLAG_SKIP_MULTICAST | GAA_FLAG_SKIP_DNS_SERVER, NULL, addresses, &bufLen) == NO_ERROR) {
        int order[2] = { prefer_ipv6 ? AF_INET6 : AF_INET, prefer_ipv6 ? AF_INET : AF_INET6 };
        for (int pass = 0; pass < 2; pass++) {
            IP_ADAPTER_ADDRESSES* adapter = addresses;
            while (adapter) {
                IP_ADAPTER_UNICAST_ADDRESS* unicast = adapter->FirstUnicastAddress;
                while (unicast) {
                    if (unicast->Address.lpSockaddr->sa_family == order[pass]) {
                        void* addrPtr = NULL;
                        if (order[pass] == AF_INET) {
                            struct sockaddr_in* ipv4 = (struct sockaddr_in*)unicast->Address.lpSockaddr;
                            if (ipv4->sin_addr.s_addr == htonl(INADDR_LOOPBACK)) { unicast = unicast->Next; continue; }
                            addrPtr = &ipv4->sin_addr;
                        }
                        else {
                            struct sockaddr_in6* ipv6 = (struct sockaddr_in6*)unicast->Address.lpSockaddr;
                            struct in6_addr loopback = IN6ADDR_LOOPBACK_INIT;
                            if (memcmp(&ipv6->sin6_addr, &loopback, sizeof(loopback)) == 0) { unicast = unicast->Next; continue; }
                            if (IN6_IS_ADDR_LINKLOCAL(&ipv6->sin6_addr)) { unicast = unicast->Next; continue; }
                            addrPtr = &ipv6->sin6_addr;
                        }
                        if (addrPtr) {
                            inet_ntop(order[pass], addrPtr, out_ip, (socklen_t)out_size);
                            free(addresses);
                            WSACleanup();
                            return;
                        }
                    }
                    unicast = unicast->Next;
                }
                adapter = adapter->Next;
            }
        }
    }
    free(addresses);
    WSACleanup();
#else
    struct ifaddrs* ifaddr, * ifa;
    if (getifaddrs(&ifaddr) == -1) {
        return;
    }
    int order[2] = { prefer_ipv6 ? AF_INET6 : AF_INET, prefer_ipv6 ? AF_INET : AF_INET6 };
    for (int pass = 0; pass < 2; pass++) {
        for (ifa = ifaddr; ifa != NULL; ifa = ifa->ifa_next) {
            if (!ifa->ifa_addr) continue;
            if (ifa->ifa_addr->sa_family == order[pass]) {
                if (if (order[pass] == AF_INET) { 
                    struct sockaddr_in* ipv4 = (struct sockaddr_in*)ifa->ifa_addr;
                    if (ipv4->sin_addr.s_addr == htonl(INADDR_LOOPBACK)) continue;
                    inet_ntop(AF_INET, &ipv4->sin_addr, out_ip, out_size);
                    freeifaddrs(ifaddr);
                    return;
                }
                else if (order[pass] == AF_INET6) { 
                    struct sockaddr_in6* ipv6 = (struct sockaddr_in6*)ifa->ifa_addr;
                    struct in6_addr loopback = IN6ADDR_LOOPBACK_INIT;
                    if (memcmp(&ipv6->sin6_addr, &loopback, sizeof(loopback)) == 0) continue;
                    if (IN6_IS_ADDR_LINKLOCAL(&ipv6->sin6_addr)) continue;
                    inet_ntop(AF_INET6, &ipv6->sin6_addr, out_ip, out_size);
                    freeifaddrs(ifaddr);
                    return;
                }
            }
        }
    }
    freeifaddrs(ifaddr);
#endif
    return;
}

int Load_Socket_Timeout(socket_t sock, int recv_timeout_ms, int send_timeout_ms) {
#if defined(_WIN32)
    DWORD rcv = recv_timeout_ms, snd = send_timeout_ms;
    if (setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, (const char*)&rcv, sizeof(rcv)) < 0) return SOCKET_ERR_TIMEOUT;
    if (setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, (const char*)&snd, sizeof(snd)) < 0) return SOCKET_ERR_TIMEOUT;
#else
    struct timeval tv;
    tv.tv_sec = recv_timeout_ms / 1000;
    tv.tv_usec = (recv_timeout_ms % 1000) * 1000;
    if (setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv)) < 0) return SOCKET_ERR_TIMEOUT;
    tv.tv_sec = send_timeout_ms / 1000;
    tv.tv_usec = (send_timeout_ms % 1000) * 1000;
    if (setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv)) < 0) return SOCKET_ERR_TIMEOUT;
#endif
    return SOCKET_OK;
}
