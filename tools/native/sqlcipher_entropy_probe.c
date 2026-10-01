/* Synthetic native regression; link the exact production SQLite object and LTC
 * archive with --wrap=fopen,--wrap=clock. No test hooks enter the shipped library.
 */
#include <stdio.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#define SQLITE_HAS_CODEC 1
#include "sqlite3.h"

static int deny_entropy;
FILE *__real_fopen(const char *, const char *);
FILE *__wrap_fopen(const char *path, const char *mode) {
    if (deny_entropy && (!strcmp(path, "/dev/random") || !strcmp(path, "/dev/urandom"))) {
        return NULL;
    }
    return __real_fopen(path, mode);
}
clock_t __wrap_clock(void) {
    /* Reaching timing entropy is an unambiguous negative-control failure. */
    puts("FAIL forbidden_clock_entropy_fallback");
    fflush(stdout);
    _exit(77);
}
int main(int argc, char **argv) {
    sqlite3 *db = NULL;
    unsigned char synthetic_secret[32];
    memset(synthetic_secret, 0x63, sizeof synthetic_secret);
    deny_entropy = argc == 2 && !strcmp(argv[1], "deny-entropy");
    int open_status = sqlite3_open(":memory:", &db);
    if (open_status != SQLITE_OK) {
        sqlite3_close(db);
        if (!deny_entropy) return 10;
        printf("PASS entropy_denied initialization_rejected=%d\n", open_status);
        return 0;
    }
    int key_status = sqlite3_key(db, synthetic_secret, sizeof synthetic_secret);
    int write_status = sqlite3_exec(db, "CREATE TABLE synthetic_fixture(value INTEGER)", NULL, NULL, NULL);
    memset(synthetic_secret, 0, sizeof synthetic_secret);
    sqlite3_close(db);
    if (deny_entropy) {
        /* A failed key operation must not authorize an encrypted write. The
         * product helper never continues after a failed key operation. */
        if (key_status == SQLITE_OK) return 11;
        printf("PASS entropy_denied key_rejected=%d\n", key_status);
        return 0;
    }
    if (key_status != SQLITE_OK || write_status != SQLITE_OK) return 12;
    puts("PASS os_entropy_available encrypted_initialization");
    return 0;
}
