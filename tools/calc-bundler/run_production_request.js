// JVM boundary integration: execute its serialized production request with the shipped bundle.
const fs = require('fs');
const vm = require('vm');
vm.runInThisContext(fs.readFileSync(process.argv[2], 'utf8'));
process.stdout.write(DualDexCalc.calculateDamage(fs.readFileSync(0, 'utf8')));
