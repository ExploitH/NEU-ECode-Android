// Thin JNI wrapper around official OpenVPN/openvpn3 ClientAPI.
// SPDX-License-Identifier: MPL-2.0
// Core sources remain in the official OpenVPN/openvpn3 tree (MPL-2.0 OR AGPL-3.0).

#include <jni.h>

#include <android/log.h>
#include <atomic>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

#define OPENVPN_LOG_CLASS openvpn::ClientAPI::LogReceiver
#define OPENVPN_LOG_INFO openvpn::ClientAPI::LogInfo
#include <client/ovpncli.hpp>

namespace {

JavaVM *g_vm = nullptr;
std::mutex g_lock;
jobject g_listener = nullptr;

struct PendingTun {
    std::string session;
    std::string remote;
    bool remote_ipv6 = false;
    std::string ipv4;
    int ipv4_prefix = 32;
    std::string ipv4_gateway;
    std::string ipv6;
    int ipv6_prefix = 128;
    int mtu = 1400;
    std::vector<std::string> dns;
    std::vector<std::string> routes4;
    std::vector<std::string> routes6;
    bool block_ipv6 = false;
};

PendingTun g_tun;
std::atomic<bool> g_stop{false};
openvpn::ClientAPI::OpenVPNClient *g_client = nullptr;

std::string jstring_to_std(JNIEnv *env, jstring value) {
    if (!value) {
        return {};
    }
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string out = chars ? chars : "";
    env->ReleaseStringUTFChars(value, chars);
    return out;
}

JNIEnv *attach_env(bool *attached) {
    *attached = false;
    JNIEnv *env = nullptr;
    if (!g_vm) {
        return nullptr;
    }
    const jint status = g_vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (status == JNI_OK) {
        return env;
    }
    if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
        return nullptr;
    }
    *attached = true;
    return env;
}

void emit_event(const std::string &name, const std::string &info, bool error, bool fatal) {
    bool attached = false;
    JNIEnv *env = attach_env(&attached);
    if (!env) {
        return;
    }
    jobject listener;
    {
        std::lock_guard<std::mutex> lock(g_lock);
        listener = g_listener;
    }
    if (listener) {
        jclass cls = env->GetObjectClass(listener);
        jmethodID mid = env->GetMethodID(cls, "onNativeEvent", "(Ljava/lang/String;Ljava/lang/String;ZZ)V");
        if (mid) {
            jstring jname = env->NewStringUTF(name.c_str());
            jstring jinfo = env->NewStringUTF(info.c_str());
            env->CallVoidMethod(listener, mid, jname, jinfo, error ? JNI_TRUE : JNI_FALSE, fatal ? JNI_TRUE : JNI_FALSE);
            env->DeleteLocalRef(jname);
            env->DeleteLocalRef(jinfo);
        }
        env->DeleteLocalRef(cls);
    }
    if (attached) {
        g_vm->DetachCurrentThread();
    }
}

void emit_log(const std::string &line) {
    __android_log_print(ANDROID_LOG_INFO, "ovpncli", "%s", line.c_str());
    bool attached = false;
    JNIEnv *env = attach_env(&attached);
    if (!env) {
        return;
    }
    jobject listener;
    {
        std::lock_guard<std::mutex> lock(g_lock);
        listener = g_listener;
    }
    if (listener) {
        jclass cls = env->GetObjectClass(listener);
        jmethodID mid = env->GetMethodID(cls, "onNativeLog", "(Ljava/lang/String;)V");
        if (mid) {
            jstring jline = env->NewStringUTF(line.c_str());
            env->CallVoidMethod(listener, mid, jline);
            env->DeleteLocalRef(jline);
        }
        env->DeleteLocalRef(cls);
    }
    if (attached) {
        g_vm->DetachCurrentThread();
    }
}

int establish_tun() {
    bool attached = false;
    JNIEnv *env = attach_env(&attached);
    if (!env) {
        return -1;
    }
    jobject listener;
    PendingTun tun;
    {
        std::lock_guard<std::mutex> lock(g_lock);
        listener = g_listener;
        tun = g_tun;
    }
    int fd = -1;
    if (listener) {
        jclass cls = env->GetObjectClass(listener);
        jmethodID mid = env->GetMethodID(
            cls,
            "onEstablishTun",
            "(Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;ILjava/lang/String;[Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;I)I");
        if (mid) {
            auto to_jarray = [&](const std::vector<std::string> &items) {
                jclass string_cls = env->FindClass("java/lang/String");
                jobjectArray arr = env->NewObjectArray(static_cast<jsize>(items.size()), string_cls, nullptr);
                for (size_t i = 0; i < items.size(); ++i) {
                    jstring s = env->NewStringUTF(items[i].c_str());
                    env->SetObjectArrayElement(arr, static_cast<jsize>(i), s);
                    env->DeleteLocalRef(s);
                }
                env->DeleteLocalRef(string_cls);
                return arr;
            };
            jstring jsession = env->NewStringUTF(tun.session.c_str());
            jstring jipv4 = env->NewStringUTF(tun.ipv4.c_str());
            jstring jgw = env->NewStringUTF(tun.ipv4_gateway.c_str());
            jstring jipv6 = env->NewStringUTF(tun.ipv6.c_str());
            jobjectArray jdns = to_jarray(tun.dns);
            jobjectArray jr4 = to_jarray(tun.routes4);
            jobjectArray jr6 = to_jarray(tun.routes6);
            fd = env->CallIntMethod(
                listener,
                mid,
                jsession,
                jipv4,
                tun.ipv4_prefix,
                jgw,
                tun.ipv6_prefix,
                jipv6,
                jdns,
                jr4,
                jr6,
                tun.mtu);
            env->DeleteLocalRef(jsession);
            env->DeleteLocalRef(jipv4);
            env->DeleteLocalRef(jgw);
            env->DeleteLocalRef(jipv6);
            env->DeleteLocalRef(jdns);
            env->DeleteLocalRef(jr4);
            env->DeleteLocalRef(jr6);
        }
        env->DeleteLocalRef(cls);
    }
    if (attached) {
        g_vm->DetachCurrentThread();
    }
    return fd;
}

bool protect_socket(int fd) {
    bool attached = false;
    JNIEnv *env = attach_env(&attached);
    if (!env) {
        return false;
    }
    jobject listener;
    {
        std::lock_guard<std::mutex> lock(g_lock);
        listener = g_listener;
    }
    jboolean ok = JNI_FALSE;
    if (listener) {
        jclass cls = env->GetObjectClass(listener);
        jmethodID mid = env->GetMethodID(cls, "onProtectSocket", "(I)Z");
        if (mid) {
            ok = env->CallBooleanMethod(listener, mid, fd);
        }
        env->DeleteLocalRef(cls);
    }
    if (attached) {
        g_vm->DetachCurrentThread();
    }
    return ok == JNI_TRUE;
}

class AndroidOpenVpnClient : public openvpn::ClientAPI::OpenVPNClient {
public:
    bool tun_builder_new() override {
        std::lock_guard<std::mutex> lock(g_lock);
        g_tun = PendingTun{};
        return true;
    }

    bool tun_builder_set_remote_address(const std::string &address, bool ipv6) override {
        std::lock_guard<std::mutex> lock(g_lock);
        g_tun.remote = address;
        g_tun.remote_ipv6 = ipv6;
        return true;
    }

    bool tun_builder_add_address(const std::string &address,
                                 int prefix_length,
                                 const std::string &gateway,
                                 bool ipv6,
                                 bool) override {
        std::lock_guard<std::mutex> lock(g_lock);
        if (ipv6) {
            g_tun.ipv6 = address;
            g_tun.ipv6_prefix = prefix_length;
        } else {
            g_tun.ipv4 = address;
            g_tun.ipv4_prefix = prefix_length;
            g_tun.ipv4_gateway = gateway;
        }
        return true;
    }

    bool tun_builder_reroute_gw(bool, bool, unsigned int) override {
        // Split-tunnel is enforced in the sanitized profile. Ignore default route.
        return true;
    }

    bool tun_builder_add_route(const std::string &address, int prefix_length, int, bool ipv6) override {
        std::lock_guard<std::mutex> lock(g_lock);
        const std::string cidr = address + "/" + std::to_string(prefix_length);
        if (ipv6) {
            g_tun.routes6.push_back(cidr);
        } else {
            g_tun.routes4.push_back(cidr);
        }
        return true;
    }

    bool tun_builder_exclude_route(const std::string &, int, int, bool) override {
        return true;
    }

    bool tun_builder_set_dns_options(const openvpn::DnsOptions &dns) override {
        std::lock_guard<std::mutex> lock(g_lock);
        g_tun.dns.clear();
        for (const auto &entry : dns.servers) {
            for (const auto &addr : entry.second.addresses) {
                if (!addr.address.empty()) {
                    g_tun.dns.push_back(addr.address);
                }
            }
        }
        return true;
    }

    bool tun_builder_set_mtu(int mtu) override {
        std::lock_guard<std::mutex> lock(g_lock);
        g_tun.mtu = mtu > 0 ? mtu : 1400;
        return true;
    }

    bool tun_builder_set_session_name(const std::string &name) override {
        std::lock_guard<std::mutex> lock(g_lock);
        g_tun.session = name;
        return true;
    }

    bool tun_builder_set_allow_family(int, bool) override {
        return true;
    }

    int tun_builder_establish() override {
        return establish_tun();
    }

    bool socket_protect(openvpn_io::detail::socket_type socket, std::string, bool) override {
        return protect_socket(static_cast<int>(socket));
    }

    bool pause_on_connection_timeout() override {
        return false;
    }

    void event(const openvpn::ClientAPI::Event &ev) override {
        emit_event(ev.name, ev.info, ev.error, ev.fatal);
    }

    void acc_event(const openvpn::ClientAPI::AppCustomControlMessageEvent &) override {}

    void log(const openvpn::ClientAPI::LogInfo &info) override {
        emit_log(info.text);
    }

    void external_pki_cert_request(openvpn::ClientAPI::ExternalPKICertRequest &req) override {
        req.error = true;
        req.errorText = "external PKI not used";
    }

    void external_pki_sign_request(openvpn::ClientAPI::ExternalPKISignRequest &req) override {
        req.error = true;
        req.errorText = "external PKI not used";
    }
};

std::unique_ptr<AndroidOpenVpnClient> g_owned;

} // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_neko_neuecode_data_vpn_NativeOpenVpn3Bridge_nativeAvailable(JNIEnv *, jobject) {
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_neko_neuecode_data_vpn_NativeOpenVpn3Bridge_nativeSetListener(JNIEnv *env, jobject, jobject listener) {
    std::lock_guard<std::mutex> lock(g_lock);
    if (g_listener) {
        env->DeleteGlobalRef(g_listener);
        g_listener = nullptr;
    }
    if (listener) {
        g_listener = env->NewGlobalRef(listener);
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_neko_neuecode_data_vpn_NativeOpenVpn3Bridge_nativeConnect(
    JNIEnv *env,
    jobject,
    jstring jprofile,
    jstring jusername,
    jstring jpassword,
    jstring jchallenge,
    jstring jcookie) {
    const std::string profile = jstring_to_std(env, jprofile);
    const std::string username = jstring_to_std(env, jusername);
    const std::string password = jstring_to_std(env, jpassword);
    const std::string challenge = jstring_to_std(env, jchallenge);
    const std::string cookie = jstring_to_std(env, jcookie);

    g_stop.store(false);
    try {
        auto client = std::make_unique<AndroidOpenVpnClient>();
        openvpn::ClientAPI::Config config;
        config.content = profile;
        config.guiVersion = "NEUeCode 5.33";
        config.connTimeout = 30;
        config.info = true;
        config.allowLocalLanAccess = true;
        // Student profile is auth-user-pass + tls-auth, no client cert.
        config.disableClientCert = true;
#ifdef OPENVPN_PLATFORM_ANDROID
        config.enableRouteEmulation = false;
#endif

        const auto eval = client->eval_config(config);
        if (eval.error) {
            __android_log_print(ANDROID_LOG_ERROR, "ovpncli", "eval_config: %s", eval.message.c_str());
            return env->NewStringUTF(eval.message.c_str());
        }

        openvpn::ClientAPI::ProvideCreds creds;
        creds.username = username;
        creds.password = password;
        if (!challenge.empty()) {
            creds.response = challenge;
            creds.dynamicChallengeCookie = cookie.empty() ? challenge : cookie;
        }
        const auto cred_status = client->provide_creds(creds);
        if (cred_status.error) {
            __android_log_print(ANDROID_LOG_ERROR, "ovpncli", "provide_creds: %s", cred_status.message.c_str());
            return env->NewStringUTF(cred_status.message.c_str());
        }

        {
            std::lock_guard<std::mutex> lock(g_lock);
            g_owned = std::move(client);
            g_client = g_owned.get();
        }

        const auto status = g_owned->connect();
        {
            std::lock_guard<std::mutex> lock(g_lock);
            g_client = nullptr;
            g_owned.reset();
        }
        if (status.error) {
            const std::string message = status.message.empty() ? status.status : status.message;
            __android_log_print(ANDROID_LOG_ERROR, "ovpncli", "connect: %s", message.c_str());
            return env->NewStringUTF(message.c_str());
        }
        return env->NewStringUTF("");
    } catch (const std::exception &ex) {
        __android_log_print(ANDROID_LOG_ERROR, "ovpncli", "exception: %s", ex.what());
        return env->NewStringUTF(ex.what());
    } catch (...) {
        __android_log_print(ANDROID_LOG_ERROR, "ovpncli", "unknown native exception");
        return env->NewStringUTF("unknown native exception");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_neko_neuecode_data_vpn_NativeOpenVpn3Bridge_nativeStop(JNIEnv *, jobject) {
    g_stop.store(true);
    openvpn::ClientAPI::OpenVPNClient *client = nullptr;
    {
        std::lock_guard<std::mutex> lock(g_lock);
        client = g_client;
    }
    if (client) {
        client->stop();
    }
}
