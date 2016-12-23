VPATH=src/linux

discid:
	cd $(VPATH) && make

install: discid
	install $(VPATH)/discid /usr/bin

uninstall:
	rm /usr/bin/discid

mac:
	cd src/mac && make

mac-install: mac
	install src/mac/discid /usr/bin
