# TODO: Remove @bazel_gazelle and @io_bazel_rules_go when using a newer release of
# rules_webtesting (>=0.3.6).
load("@bazel_gazelle//:deps.bzl", "gazelle_dependencies")
load("@io_bazel_rules_go//go:deps.bzl", "go_register_toolchains", "go_rules_dependencies")
load(
    "@io_bazel_rules_webtesting//web:go_repositories.bzl",
    "go_repositories",
    "go_internal_repositories",
)
load("@io_bazel_rules_webtesting//web:java_repositories.bzl", "java_repositories")
load("@io_bazel_rules_webtesting//web:repositories.bzl", "web_test_repositories")
load("@io_bazel_rules_webtesting//web/versioned:browsers-0.3.3.bzl", "browser_repositories")

def setup_web_test_repositories(**kwargs):
    """
    Loading dependencies needed for web testing

    Args:
      **kwargs: Set which browser repositories to be loaded.
    """

    go_rules_dependencies()

    go_register_toolchains(version = "1.16.5")

    gazelle_dependencies()

    web_test_repositories()

    browser_repositories(
        **kwargs
    )

    go_repositories()

    go_internal_repositories()

    java_repositories()

