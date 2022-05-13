# README #

## Setup

To build this documentation you need sphinx and sphinx-rtd-theme:

### Apt
```
sudo apt install python3-sphinx python3-sphinx-rtd-theme
```

### Pip
```
pip3 install sphinx sphinx_rtd_theme
echo 'export PATH="/path/to/Python/3.8/bin/:$PATH"' >> ~/.zshrc && source ~/.zshrc
```

## Create docs

To create docs for eg chromia0 you run:
```
sphinx-build -c doc/chromia0 -b html <target-folder>
```
