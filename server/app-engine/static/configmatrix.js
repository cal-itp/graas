class ConfigMatrix {
    constructor() {
        this.rows = {};
    }

    addRow(name, locator, present) {
        this.rows[name] = {name: name, locator: locator, present: present, loaded: false, selected: false};
    }

    countRows() {
        var rows = 0

        for (var prop in this.rows) {
            rows++;
        }

        return rows;
    }

    setPresent(name, present) {
        var row = this.rows[name];
        row.present = present;
    }

    setLoaded(name, loaded) {
        var row = this.rows[name];
        row.loaded = loaded;
    }

    setSelected(name, selected) {
        var row = this.rows[name];
        row.selected = selected;
    }

    getPresent(name) {
        var row = this.rows[name];
        return row.present;
    }

    getNameLocatorList() {
        var res = [];

        for (var prop in this.rows) {
            if (this.rows.hasOwnProperty(prop)) {
                var obj = this.rows[prop];
                res.push({name: obj.name, locator: obj.locator});
            }
        }

        return res;
    }

    isLoaded() {
        var res = true;

        for (var prop in this.rows) {
            if (this.rows.hasOwnProperty(prop)) {
                var obj = this.rows[prop];
                res = res && (obj.present == ConfigMatrix.NOT_PRESENT || obj.loaded);
            }
        }

        return res;
    }

    // Returns the first not-yet-loaded object in ConfigMatrix
    getNextToLoad() {
        for (var prop in this.rows) {
            if (this.rows.hasOwnProperty(prop)) {
                var obj = this.rows[prop];
                if(obj.loaded == false) {
                    return {name: obj.name, locator: obj.locator};
                    break;
                }
            }
        }
        return null;
    }

    isComplete() {
        var res = true;

        for (var prop in this.rows) {
            if (this.rows.hasOwnProperty(prop)) {
                var obj = this.rows[prop];
                res = res && (obj.present == ConfigMatrix.NOT_PRESENT || obj.selected);
            }
        }

        return res;
    }
}

ConfigMatrix.UNKNOWN = 'unknown';
ConfigMatrix.PRESENT = 'present';
ConfigMatrix.NOT_PRESENT = 'not present';

