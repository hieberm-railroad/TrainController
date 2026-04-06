#include <unity.h>

void test_smoke_math()
{
    TEST_ASSERT_EQUAL_INT(4, 2 + 2);
}

int main(int argc, char **argv)
{
    UNITY_BEGIN();
    RUN_TEST(test_smoke_math);
    return UNITY_END();
}
