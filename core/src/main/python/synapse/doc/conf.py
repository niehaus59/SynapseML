# Copyright (C) Microsoft Corporation. All rights reserved.
# Licensed under the MIT License. See LICENSE in project root for information.

# Based on code generated by `sphinx-autogen`.
# This file is execfile()d with the current directory set to its
# containing dir.

# -- General configuration ------------------------------------------------

# Add any Sphinx extension module names here, as strings. They can be
# extensions coming with Sphinx (named "sphinx.ext.*") or your custom
# ones.
extensions = [
    "sphinx.ext.autodoc",
    "sphinx.ext.doctest",
    "sphinx.ext.intersphinx",
    "sphinx.ext.mathjax",
    "sphinx.ext.ifconfig",
    "sphinx.ext.viewcode",
    "sphinx.ext.napoleon",
    "sphinx_paramlinks",
]

# Add any paths that contain templates here, relative to this directory.
# templates_path = ["_templates"]


source_suffix = [".rst", ".md"]

# The master toctree document.
master_doc = "index"

# General information about the project.
project = "Microsoft Machine Learning for Apache Spark"
copyright = "2017, Microsoft"
author = "Microsoft"

# The version info for the project you're documenting, acts as replacement for
# |version| and |release|, also used in various other places throughout the
# built documents.
# version = "1.0"   # The short X.Y version.
# release = "1.0.0" # The full version, including alpha/beta/rc tags.

# The language for content autogenerated by Sphinx. Refer to documentation
# for a list of supported languages.
#
# This is also used if you do content translation via gettext catalogs.
# Usually you set "language" from the command line for these cases.
language = None

# List of patterns, relative to source directory, that match files and
# directories to ignore when looking for source files.
# These patterns also affect html_static_path and html_extra_path
exclude_patterns = []

# The name of the Pygments (syntax highlighting) style to use.
pygments_style = "sphinx"

# If true, `todo` and `todoList` produce output, else they produce nothing.
todo_include_todos = False


# -- Options for HTML output ----------------------------------------------

# The theme to use for HTML and HTML Help pages.  See the documentation for
# a list of builtin themes.
html_theme = "sphinx_rtd_theme"

# Theme options are theme-specific and customize the look and feel of a theme
# further.  For a list of options available for each theme, see the
# documentation.
# html_theme_options = {}

# Add any paths that contain custom static files (such as style sheets) here,
# relative to this directory. They are copied after the builtin static files,
# so a file named "default.css" will overwrite the builtin "default.css".
# html_static_path = ["_static"]


# -- Options for HTMLHelp output ------------------------------------------

# Output file base name for HTML help builder.
htmlhelp_basename = "SynapseMLdoc"

# -- Options for manual page output ---------------------------------------

# One entry per manual page. List of tuples
# (source start file, name, description, authors, manual section).
man_pages = [(master_doc, "synapseml", "SynapseML Documentation", [author], 1)]


# -- Options for Texinfo output -------------------------------------------

# Grouping the document tree into Texinfo files. List of tuples
# (source start file, target name, title, author,
#  dir menu entry, description, category)
texinfo_documents = [
    (
        master_doc,
        "SynapseML",
        "SynapseML Documentation",
        author,
        "SynapseML",
        "One line description of project.",
        "Miscellaneous",
    ),
]


# Example configuration for intersphinx: refer to the Python standard library.
intersphinx_mapping = {
    "python": ("https://docs.python.org/3", None),
    "torch": ("https://pytorch.org/docs/stable/", None),
    "numpy": ("https://numpy.org/doc/stable/", None),
    "pytorch_lightning": ("https://pytorch-lightning.readthedocs.io/en/stable/", None),
    "torchvision": ("https://pytorch.org/vision/stable/", None),
}
# intersphinx_mapping = { "scala": ("/scala/index.html", None) }

# -- Mock out pandas that can't be found ----------------------------
autodoc_mock_imports = ["pandas"]

# -- Setup AutoStructify --------------------------------------------------
# Use this if we ever want to use markdown pages instead of rst pages.
# (note: currently, this requires pip-installing "sphinx==1.5.6" because of an
# obscure bug, see rtfd/recommonmark#73 and sphinx-doc/sphinx#3800)
# from recommonmark.transform import AutoStructify
# def synapseml_doc_resolver(path):
#     return path # github_doc_root + url
# def setup(app):
#     app.add_config_value("recommonmark_config", {
#         "url_resolver": synapseml_doc_resolver,
#         "auto_toc_tree_section": "Contents",
#         "enable_eval_rst": True,
#         "enable_auto_doc_ref": True,
#     }, True)
#     app.add_transform(AutoStructify)
